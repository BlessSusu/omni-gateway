"""OmniGateway simple-frame 协议编解码（压测脚本共用）。"""

from __future__ import annotations

import json
import struct
from typing import Any

MAGIC = b"OMNI"


def checksum(magic: bytes, body: bytes) -> int:
    x = 0
    for b in magic + body:
        x ^= b
    return x & 0xFF


def encode_frame(obj: dict[str, Any]) -> bytes:
    body = json.dumps(obj, separators=(",", ":")).encode("utf-8")
    return MAGIC + struct.pack(">H", len(body)) + body + bytes([checksum(MAGIC, body)])
