# Risk register

| ID | Risk | Severity | Owner | Mitigation / status |
|---|---|---|---|---|
| R1 | ~~Python GTIN module not yet written~~ Closed 2026-11-23: `services/api/app/gtin` passes `gtin.json` + property tests | — | Eng | closed |
| R2 | Hindi strings are drafts by the engineering agent, not a native speaker | Medium | Product | Native-speaker review before any release track |
| R3 | ~~Product detail route only shows the resolved GTIN~~ Closed 2026-11-23: full product, search, compare, library and contribute flows against the API | — | Eng | closed |
| R4 | White text on orange `#F97316` is about 2.8:1, below WCAG AA even for large text | Medium | Design | Bold 16 sp labels for now; pick a darker orange for text-bearing fills (e.g. `#C2410C`) before release |
| R5 | Nothing has run on an emulator or a real device in CI; camera scanning is only unit-tested (stabiliser logic) | High | Eng | Manual test on the Nothing Phone with `docs/testing/seed-barcodes.html`; add instrumented tests on Test Lab |
| R6 | Live API test (`FOODLENS_LIVE_API`) is opt-in and not part of `make test` | Low | Eng | Run it before each release; add to CI once the API runs in CI |
