# Hotel API Gateway

Standalone Spring Cloud Gateway for independently deployable HotelAI microservices. Every public
API group has an explicit owner; there is no catch-all route or legacy backend fallback. The
compatibility `/api/v1/me` alias is owned by Customer Service.

## Build and run

```powershell
mvn test
mvn spring-boot:run
```

The gateway validates bearer JWTs with the Identity Service JWK set. Set these variables for local
development:

| Variable | Default |
| --- | --- |
| `IDENTITY_SERVICE_URL` | `http://identity-service:8101` |
| `CUSTOMER_SERVICE_URL` | `http://customer-service:8102` |
| `NOTIFICATION_SERVICE_URL` | `http://notification-service:8111` |
| `AI_CONVERSATION_SERVICE_URL` | `http://ai-conversation-service:8000` |
| `AI_RECOMMENDATION_SERVICE_URL` | `http://ai-recommendation-service:8000` |
| `AI_FORECASTING_SERVICE_URL` | `http://ai-forecasting-service:8000` |
| `AI_REVENUE_SERVICE_URL` | `http://ai-revenue-service:8000` |
| `AI_CUSTOMER_ANALYTICS_SERVICE_URL` | `http://ai-customer-analytics-service:8000` |
| `JWT_ISSUER_URI` | `http://identity-service:8101` |
| `JWT_JWK_SET_URI` | `http://identity-service:8101/.well-known/jwks.json` |
| `JWT_AUDIENCE` | `hotel-api` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `GATEWAY_MAX_REQUEST_SIZE` | `2MB` |
| `GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS` | local frontend origins |

Auth routes use a Redis token-bucket limit. A valid inbound `X-Correlation-Id` is propagated to
downstream services and returned to the caller; unsafe or absent values are replaced with a UUID.
Gateway-generated errors use `application/problem+json`.

Authenticated access tokens must contain a `sid` claim. The gateway rejects sessions whose Redis
key `identity:revoked-session:{sid}` exists. Redis failure is fail-closed for administration,
customer data, booking/payment/operation/notification routes, and every non-read request; actuator
readiness also includes the Redis health contributor.
