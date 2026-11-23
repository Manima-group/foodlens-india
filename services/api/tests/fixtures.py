import json
from pathlib import Path
from typing import Any

FIXTURES = Path(__file__).resolve().parents[3] / "packages" / "fixtures"


def load(name: str) -> Any:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))
