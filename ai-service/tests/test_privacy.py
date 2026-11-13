from app.config import Settings
from app.privacy import redact_external_text


def test_redacts_direct_identifiers_before_external_ai():
    safe = redact_external_text("Email guest@example.com, điện thoại 0912345678, CCCD 012345678901, thẻ 4111 1111 1111 1111")

    assert "guest@example.com" not in safe
    assert "0912345678" not in safe
    assert "012345678901" not in safe
    assert "4111 1111 1111 1111" not in safe
    assert "[EMAIL_DA_CHE]" in safe


def test_provider_requires_matching_key():
    assert Settings(ai_provider="gemini", gemini_api_key=None).active_provider == "fallback"
    assert Settings(ai_provider="gemini", gemini_api_key="configured").active_provider == "gemini"
    assert Settings(ai_provider="openai", openai_api_key="configured").active_provider == "openai"
