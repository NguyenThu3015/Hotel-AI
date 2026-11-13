[CmdletBinding()]
param(
    [string]$ComposeFile = 'infrastructure/docker-compose.microservices.yml',
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345',
    [switch]$SkipAiSmokeTests
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sqlPath = Join-Path $PSScriptRoot 'ai-demo-data.sql'
$knowledgeSqlPath = Join-Path $PSScriptRoot 'ai-demo-knowledge.sql'
$postgresContainer = 'hotel-ai-microservices-postgres-1'
$containerSqlPath = '/tmp/hotelai-ai-demo-data.sql'
$containerKnowledgeSqlPath = '/tmp/hotelai-ai-demo-knowledge.sql'

if (-not (Test-Path -LiteralPath $sqlPath)) {
    throw "Seed SQL file not found: $sqlPath"
}
if (-not (Test-Path -LiteralPath $knowledgeSqlPath)) {
    throw "Knowledge seed SQL file not found: $knowledgeSqlPath"
}

Push-Location $root
try {
    $postgresId = docker compose -f $ComposeFile ps -q postgres
    if ([string]::IsNullOrWhiteSpace($postgresId)) {
        throw 'PostgreSQL is not running. Start Docker Compose before seeding data.'
    }

    docker cp $sqlPath "${postgresContainer}:${containerSqlPath}" | Out-Null
    docker exec $postgresContainer psql -U hotel_admin -d postgres -v ON_ERROR_STOP=1 -f $containerSqlPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not load the AI demo dataset into PostgreSQL.' }

    docker cp $knowledgeSqlPath "${postgresContainer}:${containerKnowledgeSqlPath}" | Out-Null
    docker exec $postgresContainer psql -U hotel_admin -d postgres -v ON_ERROR_STOP=1 -f $containerKnowledgeSqlPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not load the AI knowledge dataset into PostgreSQL.' }

    # Reindex only the refreshed RAG documents. The startup hook generates
    # embeddings and keeps the service healthy even if an external provider is
    # temporarily unavailable (keyword retrieval remains available).
    $conversationId = docker compose -f $ComposeFile ps -q ai-conversation-service
    if (-not [string]::IsNullOrWhiteSpace($conversationId)) {
        docker compose -f $ComposeFile restart ai-conversation-service | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restart the AI conversation service.' }
        $conversationContainer = 'hotel-ai-microservices-ai-conversation-service-1'
        $conversationHealthy = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            Start-Sleep -Seconds 2
            $health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $conversationContainer 2>$null
            if ($health -eq 'healthy') {
                $conversationHealthy = $true
                break
            }
        }
        if (-not $conversationHealthy) { throw 'AI conversation service did not become healthy after indexing knowledge documents.' }
    }

    # Revenue Optimization needs a floor/ceiling pair for each sellable room
    # type.  Read canonical base rates and create realistic 75%-150% bounds.
    $rateQuery = @"
SELECT r.id, r.code, b.amount
FROM room_type_references r
JOIN LATERAL (
    SELECT amount FROM base_rate_versions b
    WHERE b.room_type_id=r.id AND b.amount>0
    ORDER BY b.version_no DESC LIMIT 1
) b ON true
WHERE r.active
  AND r.code IN ('STANDARD','SUPERIOR','DELUXE','TWIN','FAMILY','EXECUTIVE','SUITE','PRESIDENTIAL')
ORDER BY r.code;
"@
    $rateRows = @(docker exec $postgresContainer psql -U hotel_admin -d pricing_db -At -F '|' -c $rateQuery)
    $constraintCount = 0
    foreach ($row in $rateRows) {
        if ([string]::IsNullOrWhiteSpace($row)) { continue }
        $parts = $row -split '\|'
        if ($parts.Count -ne 3 -or $parts[0] -notmatch '^[0-9a-f-]{36}$') { continue }
        $amount = [decimal]::Parse($parts[2], [Globalization.CultureInfo]::InvariantCulture)
        $floor = [Math]::Round($amount * 0.75, 0)
        $ceiling = [Math]::Round($amount * 1.50, 0)
        $constraintSql = "INSERT INTO revenue_price_constraints(room_type_id,floor_price,ceiling_price,version) VALUES('$($parts[0])',$floor,$ceiling,0) ON CONFLICT(room_type_id) DO UPDATE SET floor_price=EXCLUDED.floor_price,ceiling_price=EXCLUDED.ceiling_price,version=CASE WHEN revenue_price_constraints.floor_price<>EXCLUDED.floor_price OR revenue_price_constraints.ceiling_price<>EXCLUDED.ceiling_price THEN revenue_price_constraints.version+1 ELSE revenue_price_constraints.version END,updated_at=CURRENT_TIMESTAMP;"
        docker exec $postgresContainer psql -U hotel_admin -d ai_revenue_db -v ON_ERROR_STOP=1 -c $constraintSql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not create revenue constraints for $($parts[1])." }
        $constraintCount++
    }

    $smoke = [ordered]@{}
    if (-not $SkipAiSmokeTests) {
        $loginBody = @{email=$AdminEmail;password=$AdminPassword} | ConvertTo-Json -Compress
        $login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -ContentType 'application/json; charset=utf-8' -Body $loginBody -TimeoutSec 45
        $headers = @{Authorization="Bearer $($login.accessToken)"}

        $forecast = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/ai/forecast/backtest" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{
            hotel_id=1;history_days=365;holdout_days=30
        } | ConvertTo-Json -Compress) -TimeoutSec 60
        $smoke.forecast = [ordered]@{
            evaluated=$forecast.evaluated;samples=$forecast.samples
            maeRooms=$forecast.maeRooms;mape=$forecast.mape
        }

        # Keep repeated seed runs deterministic: remove earlier scores for this
        # synthetic cohort before invoking the real model again.
        docker exec $postgresContainer psql -U hotel_admin -d ai_customer_analytics_db -v ON_ERROR_STOP=1 -c "DELETE FROM booking_risk_scores WHERE reservation_reference LIKE 'AIRISK%';" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not refresh synthetic booking risk scores.' }
        $risks = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/ai/risks/bookings/score" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{
            hotel_id=1;horizon_days=30
        } | ConvertTo-Json -Compress) -TimeoutSec 60
        $riskItems = @($risks.items)
        $smoke.bookingRisk = [ordered]@{
            total=$riskItems.Count
            high=@($riskItems | Where-Object riskLevel -eq 'HIGH').Count
            medium=@($riskItems | Where-Object riskLevel -eq 'MEDIUM').Count
            low=@($riskItems | Where-Object riskLevel -eq 'LOW').Count
        }

        $anomalies = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/ai/anomalies" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (@{
            hotel_id=1;lookback_days=90;z_threshold=2.5
        } | ConvertTo-Json -Compress) -TimeoutSec 60
        $smoke.anomalies = [ordered]@{riskLevel=$anomalies.riskLevel;total=@($anomalies.items).Count}
    }

    $counts = [ordered]@{}
    foreach ($entry in @(
        @('customer_db','customer_profiles'),
        @('customer_db','customer_notes'),
        @('reporting_audit_db','reservation_facts'),
        @('reporting_audit_db','payment_facts'),
        @('reporting_audit_db','service_usage_facts'),
        @('review_db','reviews'),
        @('ai_customer_analytics_db','review_analyses'),
        @('ai_customer_analytics_db','booking_risk_scores'),
        @('ai_revenue_db','revenue_price_constraints'),
        @('ai_conversation_db','documents'),
        @('ai_conversation_db','document_chunks')
    )) {
        $value = docker exec $postgresContainer psql -U hotel_admin -d $entry[0] -At -c "SELECT count(*) FROM $($entry[1]);"
        $counts["$($entry[0]).$($entry[1])"] = [int]$value
    }

    [pscustomobject]@{
        result='PASS'
        marker='HOTELAI_AI_DEMO_V2'
        revenueConstraints=$constraintCount
        counts=$counts
        aiSmokeTests=$smoke
    } | ConvertTo-Json -Depth 8
}
finally {
    Pop-Location
}
