# Risk register

| ID | Risk | Severity | Owner | Mitigation / status |
|---|---|---|---|---|
| R1 | ~~Python GTIN module not yet written~~ Closed 2026-11-23: `services/api/app/gtin` passes `gtin.json` + property tests | — | Eng | closed |
| R2 | Hindi strings are drafts by the engineering agent, not a native speaker | Medium | Product | Native-speaker review before any release track |
| R3 | Product detail route only shows the resolved GTIN; Tier 1 DoD not met yet | High | Eng | Next: :core:database, :core:data, :feature:product against the API |
