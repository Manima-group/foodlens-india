"""Organic alternatives, SPEC §9. Objective factors only; never imports billing/campaigns (T1, import-linter).

Sponsored items are returned by the caller in a separate array and are never interleaved here.
"""

from collections.abc import Sequence
from dataclasses import dataclass

COMPARABLE = {"methodology_pending", "scored", "unverified_provisional"}


@dataclass(frozen=True)
class Candidate:
    product_id: str
    name: str
    category_id: str | None
    unit_dimension: str  # "mass" | "volume"
    analysis_status: str
    high_count: int
    medium_count: int
    missing_count: int


def eligible(baseline: Candidate, other: Candidate) -> bool:
    return (
        other.product_id != baseline.product_id
        and other.category_id is not None
        and other.category_id == baseline.category_id
        and other.unit_dimension == baseline.unit_dimension
        and other.analysis_status in COMPARABLE
    )


def rank(baseline: Candidate, candidates: Sequence[Candidate], limit: int = 10) -> list[Candidate]:
    """Fewer 'high' indicators, then fewer 'medium', then more complete data, then name."""
    pool = [c for c in candidates if eligible(baseline, c)]
    pool.sort(key=lambda c: (c.high_count, c.medium_count, c.missing_count, c.name.casefold()))
    return pool[:limit]
