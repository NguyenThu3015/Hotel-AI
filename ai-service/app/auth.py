import asyncio
from dataclasses import dataclass
from uuid import UUID

import jwt
from fastapi import HTTPException

from app.config import get_settings


@dataclass(frozen=True)
class StaffIdentity:
    identity_id: UUID
    roles: frozenset[str]
    permissions: frozenset[str]


async def require_staff(
    authorization: str | None,
    allowed_roles: set[str] | None = None,
) -> StaffIdentity:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Cần đăng nhập bằng tài khoản nhân viên.")
    settings = get_settings()
    token = authorization.removeprefix("Bearer ").strip()

    def decode() -> dict:
        signing_key = jwt.PyJWKClient(settings.jwt_jwk_set_uri).get_signing_key_from_jwt(token)
        return jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            audience=settings.jwt_audience,
            issuer=settings.jwt_issuer,
        )

    try:
        claims = await asyncio.to_thread(decode)
        identity_id = UUID(str(claims.get("uid") or claims.get("sub")))
    except Exception as error:
        raise HTTPException(status_code=401, detail="Access token không hợp lệ.") from error
    roles = _claim_values(claims.get("roles"), uppercase=True)
    permissions = _claim_values(claims.get("permissions"), uppercase=False)
    staff_roles = {"STAFF", "RECEPTIONIST", "HOUSEKEEPER", "MANAGER", "ADMIN"}
    if not roles & staff_roles:
        raise HTTPException(status_code=403, detail="Chức năng này chỉ dành cho nhân viên.")
    if allowed_roles and not roles & allowed_roles:
        raise HTTPException(status_code=403, detail="Vai trò hiện tại không đủ quyền thực hiện thao tác.")
    return StaffIdentity(identity_id, frozenset(roles), frozenset(permissions))


def _claim_values(value: object, uppercase: bool) -> set[str]:
    if isinstance(value, list):
        values = [str(item) for item in value]
    elif isinstance(value, str):
        values = value.split(",")
    else:
        values = []
    normalized = {item.strip() for item in values if item.strip()}
    return {item.upper() for item in normalized} if uppercase else normalized
