# Risk register

| ID | Risk | Severity | Owner | Mitigation / status |
|---|---|---|---|---|
| R1 | Python GTIN module not yet written; Kotlin is the only implementation passing `gtin.json` | High (gate 1) | Eng | Write `services/api/app/gtin` against the same fixtures in the API workstream |
| R2 | Hindi strings are drafts by the engineering agent, not a native speaker | Medium | Product | Native-speaker review before any release track |
| R3 | Product detail route only shows the resolved GTIN; Tier 1 DoD not met yet | High | Eng | Next: :core:database, :core:data, :feature:product against the API |
