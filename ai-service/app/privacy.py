import re

EMAIL = re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")
PHONE = re.compile(r"(?<!\d)(?:\+?84|0)(?:[ .-]?\d){9,10}(?!\d)")
IDENTITY_NUMBER = re.compile(r"(?<!\d)\d{9,12}(?!\d)")
PAYMENT_CARD = re.compile(r"(?<!\d)(?:\d[ -]?){13,19}(?!\d)")


def redact_external_text(value: str) -> str:
    """Remove direct identifiers before text is sent to a free external AI tier."""
    redacted = EMAIL.sub("[EMAIL_DA_CHE]", value)
    redacted = PHONE.sub("[SO_DIEN_THOAI_DA_CHE]", redacted)
    redacted = PAYMENT_CARD.sub("[SO_THE_DA_CHE]", redacted)
    return IDENTITY_NUMBER.sub("[GIAY_TO_DA_CHE]", redacted)
