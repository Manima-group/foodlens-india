# ADR 0006 — Consumer branding "AAHAR"

- Status: accepted (requested by the product owner, 2026-11-23)

## Decision
- App name: **AAHAR**. Tagline: **Know What You Eat.**
- Colours: primary orange `#F97316`, white `#FFFFFF`, grey `#6B7280`.
- The Android application ID and Kotlin package stay `com.manima.foodlens`, so existing installs upgrade in place. Repository and module names stay `foodlens-*`, since they are internal.

## Consequences
"AAHAR" still needs trademark and domain clearance before release (SPEC §18; `docs/BLOCKERS.md`).

Orange `#F97316` on white has a contrast ratio of about 2.8:1, which fails WCAG AA for body text. Orange is therefore used for fills (buttons, the app bar, chips) with white text at large or bold sizes. Body text stays dark or grey. The status banners never rely on colour alone (SPEC §10.9).
