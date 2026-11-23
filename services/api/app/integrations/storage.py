"""Object storage adapter (SPEC §5, §14).

ADR 0004: local filesystem adapter for dev; S3/MinIO adapter pending.
"""

from pathlib import Path
from typing import Protocol

MAGIC = {
    "image/jpeg": (b"\xff\xd8\xff",),
    "image/png": (b"\x89PNG\r\n\x1a\n",),
    "image/webp": (b"RIFF",),
}


def magic_ok(content_type: str, head: bytes) -> bool:
    if content_type == "image/webp":
        return head.startswith(b"RIFF") and head[8:12] == b"WEBP"
    return any(head.startswith(m) for m in MAGIC.get(content_type, ()))


class ObjectStorage(Protocol):
    def put(self, key: str, data: bytes) -> None: ...

    def exists(self, key: str) -> bool: ...

    def size(self, key: str) -> int: ...


class LocalStorage:
    def __init__(self, root: Path) -> None:
        self.root = root

    def _path(self, key: str) -> Path:
        path = (self.root / key).resolve()
        if not path.is_relative_to(self.root.resolve()):
            raise ValueError("invalid key")
        return path

    def put(self, key: str, data: bytes) -> None:
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def exists(self, key: str) -> bool:
        return self._path(key).is_file()

    def size(self, key: str) -> int:
        return self._path(key).stat().st_size
