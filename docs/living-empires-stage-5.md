# Stage 5 — Advanced Diplomacy implementation plan

Status: In development. Do not merge without Android checks, simulations, and player approval.

## Existing engine findings
- `DiplomacyManager.kt` already contains `RelationshipLevel`, `DiplomacyFlags`, and `DiplomaticModifiers`.
- Existing modifiers include denunciation, war declarations, betrayal, friendship, defensive pacts and embassy effects.
- Preserve these existing mechanisms; do not add parallel reputation/relationship storage until persistence and AI usage have been traced.

## Implementation order
1. Audit diplomatic modifier decay, relationship calculation, AI diplomacy and treaty persistence.
2. Implement bounded, persistent reputation effects for treaty betrayal and aggression, reusing existing modifiers.
3. Add war exhaustion with explicit per-war lifecycle, peace reset/decay, and save/load support.
4. Integrate denouncements and demands with existing player trade and AI decision interfaces.
5. Expand AI strategy using trust, relative military strength, war duration and exhaustion.
6. Validate AI-vs-AI and player-vs-AI paths, treaty expiry, peace negotiations, save/load, and Android stability.

## Gates
- Preserve all Stage 2–4 diplomacy behavior.
- Tests for each feature and cross-feature regression.
- Android debug APK build and simulation checks pass.
- Human gameplay verification before merge.
- Embassy expansion remains a separate roadmap integration unless explicitly included in this stage.

## Notes
This plan is not a claim that features are implemented or validated.
