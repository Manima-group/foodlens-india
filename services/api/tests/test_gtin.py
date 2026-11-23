"""SPEC §7 + quality gate 1: the same vectors the Kotlin module passes."""

import pytest
from hypothesis import given
from hypothesis import strategies as st

from app.gtin import Ambiguous, GtinErrorCode, Invalid, Valid, compute_check_digit, normalize
from tests.fixtures import load

CASES = load("gtin.json")["cases"]


def test_all_spec_rows_present() -> None:
    assert len(CASES) >= 14


@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
def test_vector(case: dict[str, object]) -> None:
    result = normalize(str(case["input"]), case.get("symbology"))  # type: ignore[arg-type]
    if "expected" in case:
        expected = case["expected"]
        assert isinstance(result, Valid), result
        assert isinstance(expected, dict)
        assert result.gtin14 == expected["gtin14"]
        assert result.is_restricted_circulation == expected["is_restricted_circulation"]
        assert result.original_code == "".join(ch for ch in str(case["input"]) if ch.isdigit())
    elif case["expected_error"] == "AMBIGUOUS_BARCODE":
        assert isinstance(result, Ambiguous), result
        got = [(c.gtin14, c.symbology) for c in result.candidates]
        want = [(c["gtin14"], c["symbology"]) for c in case["expected_candidates"]]  # type: ignore[union-attr]
        assert got == want
    else:
        assert isinstance(result, Invalid), result
        assert result.code == GtinErrorCode(case["expected_error"])


@given(
    st.integers(min_value=11, max_value=13).flatmap(lambda n: st.text("0123456789", min_size=n, max_size=n))
)
def test_check_digit_round_trip_and_idempotent(body: str) -> None:
    code = body + str(compute_check_digit(body))
    first = normalize(code)
    assert isinstance(first, Valid)
    assert first.gtin14.endswith(code) and len(first.gtin14) == 14
    second = normalize(first.gtin14)
    assert isinstance(second, Valid) and second.gtin14 == first.gtin14


@given(
    st.integers(min_value=11, max_value=13).flatmap(lambda n: st.text("0123456789", min_size=n, max_size=n)),
    st.integers(min_value=1, max_value=9),
)
def test_wrong_check_digit_rejected(body: str, offset: int) -> None:
    wrong = (compute_check_digit(body) + offset) % 10
    result = normalize(body + str(wrong))
    assert isinstance(result, Invalid) and result.code is GtinErrorCode.INVALID_CHECK_DIGIT
