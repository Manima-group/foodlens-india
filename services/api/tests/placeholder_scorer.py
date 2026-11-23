"""TEST ONLY (SPEC §9.6). import-linter forbids importing this from app/."""

from app.analysis.engine import NormalizedNutrition


class TestOnlyPlaceholderScorer:
    __test__ = False

    def score(self, normalized: NormalizedNutrition) -> float:
        return 0.0
