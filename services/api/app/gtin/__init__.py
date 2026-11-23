"""GTIN normalisation, SPEC §7. Mirrors :core:gtin (Kotlin); both pass packages/fixtures/gtin.json.

ADR 0002 records the details SPEC leaves open. GTINs are always strings; leading zeros are never stripped.
"""

from dataclasses import dataclass, field
from enum import StrEnum


class GtinErrorCode(StrEnum):
    INVALID_BARCODE = "INVALID_BARCODE"
    INVALID_CHECK_DIGIT = "INVALID_CHECK_DIGIT"
    AMBIGUOUS_BARCODE = "AMBIGUOUS_BARCODE"


@dataclass(frozen=True)
class Valid:
    gtin14: str
    original_code: str
    symbology: str
    is_restricted_circulation: bool


@dataclass(frozen=True)
class Invalid:
    code: GtinErrorCode


@dataclass(frozen=True)
class Ambiguous:
    original_code: str
    candidates: list[Valid] = field(default_factory=list)
    code: GtinErrorCode = GtinErrorCode.AMBIGUOUS_BARCODE


GtinResult = Valid | Invalid | Ambiguous

_RESTRICTED = {"02", "04", *(str(n) for n in range(20, 30))}
_LENGTH_FOR = {"EAN_8": 8, "UPC_E": 8, "UPC_A": 12, "EAN_13": 13, "GTIN_14": 14}
_DEFAULT_SYMBOLOGY = {12: "UPC_A", 13: "EAN_13", 14: "GTIN_14"}


def compute_check_digit(body: str) -> int:
    if not body or not body.isascii() or not body.isdigit():
        raise ValueError("body must be non-empty digits")
    total = sum(int(c) * (3 if i % 2 == 0 else 1) for i, c in enumerate(reversed(body)))
    return (10 - total % 10) % 10


def has_valid_check_digit(code: str) -> bool:
    return (
        len(code) >= 2
        and code.isascii()
        and code.isdigit()
        and compute_check_digit(code[:-1]) == int(code[-1])
    )


def expand_upc_e(code: str) -> str | None:
    """`N d1..d6 C` → 12-digit UPC-A, or None when N is not 0/1."""
    n, d, c = code[0], code[1:7], code[7]
    if n not in "01":
        return None
    d1, d2, d3, d4, d5, d6 = tuple(d)
    if d6 in "012":
        manufacturer, product = f"{d1}{d2}{d6}00", f"00{d3}{d4}{d5}"
    elif d6 == "3":
        manufacturer, product = f"{d1}{d2}{d3}00", f"000{d4}{d5}"
    elif d6 == "4":
        manufacturer, product = f"{d1}{d2}{d3}{d4}0", f"0000{d5}"
    else:
        manufacturer, product = f"{d1}{d2}{d3}{d4}{d5}", f"0000{d6}"
    return f"{n}{manufacturer}{product}{c}"


def is_restricted_circulation(gtin14: str) -> bool:
    return len(gtin14) == 14 and gtin14[0] == "0" and gtin14[1:3] in _RESTRICTED


def _plain(original: str, digits: str, symbology: str) -> Valid | Invalid:
    if not has_valid_check_digit(digits):
        return Invalid(GtinErrorCode.INVALID_CHECK_DIGIT)
    gtin14 = digits.zfill(14)
    return Valid(gtin14, original, symbology, is_restricted_circulation(gtin14))


def _upc_e(original: str, digits: str) -> Valid | Invalid:
    upc_a = expand_upc_e(digits)
    if upc_a is None:
        return Invalid(GtinErrorCode.INVALID_BARCODE)
    return _plain(original, upc_a, "UPC_E")


def normalize(raw: str, symbology: str | None = None) -> GtinResult:
    """symbology: scanner-reported SPEC §2 value, or None / "MANUAL" for manual entry."""
    digits = "".join(ch for ch in raw if not ch.isspace() and ch != "-")
    if not digits or not digits.isascii() or not digits.isdigit():
        return Invalid(GtinErrorCode.INVALID_BARCODE)

    if symbology not in (None, "MANUAL"):
        if symbology not in _LENGTH_FOR or len(digits) != _LENGTH_FOR[symbology]:
            return Invalid(GtinErrorCode.INVALID_BARCODE)
        return _upc_e(digits, digits) if symbology == "UPC_E" else _plain(digits, digits, symbology)

    if len(digits) == 8:
        ean8 = _plain(digits, digits, "EAN_8")
        upce = _upc_e(digits, digits) if digits[0] in "01" else None
        ean8_ok = ean8 if isinstance(ean8, Valid) else None
        upce_ok = upce if isinstance(upce, Valid) else None
        if ean8_ok and upce_ok and ean8_ok.gtin14 != upce_ok.gtin14:
            return Ambiguous(digits, [ean8_ok, upce_ok])
        return ean8_ok or upce_ok or Invalid(GtinErrorCode.INVALID_CHECK_DIGIT)
    if len(digits) in _DEFAULT_SYMBOLOGY:
        return _plain(digits, digits, _DEFAULT_SYMBOLOGY[len(digits)])
    return Invalid(GtinErrorCode.INVALID_BARCODE)
