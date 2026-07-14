"""录制阶段敏感字段脱敏（与前端规则对齐）"""

import re

_PHONE = re.compile(r"(?<!\d)(1[3-9]\d{9})(?!\d)")
_ID = re.compile(r"(?<!\d)(\d{17}[\dXx])(?!\d)")
_EMAIL = re.compile(r"([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})")
_BANK = re.compile(r"(?<!\d)(\d{16,19})(?!\d)")


def desensitize_text(text: str) -> str:
    if not text:
        return text
    out = text
    out = _PHONE.sub(lambda m: m.group(1)[:3] + "****" + m.group(1)[-4:], out)
    out = _ID.sub(lambda m: m.group(1)[:4] + "**********" + m.group(1)[-4:], out)

    def _email(m):
        user, domain = m.group(1), m.group(2)
        return (user[:2] if len(user) > 2 else user) + "***@" + domain

    out = _EMAIL.sub(_email, out)
    out = _BANK.sub(lambda m: m.group(1)[:4] + " **** **** " + m.group(1)[-4:], out)
    return out


def contains_sensitive(text: str) -> bool:
    if not text:
        return False
    return bool(_PHONE.search(text) or _ID.search(text) or _EMAIL.search(text) or _BANK.search(text))
