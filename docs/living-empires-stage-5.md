# Stage 5 — Advanced Diplomacy and Embassy Foundation

Status: In development. Draft PR only. Do not merge without passing Android checks, automated regression and simulation, save/load verification, human gameplay acceptance, and explicit approval.

## Approved direction
Stage 5 establishes the central foreign-relations framework for Living Empires, integrating the Advanced Embassy Expansion with the diplomacy mechanics developed in Stages 2–4. Preserve the locked Stage 2–4 gameplay and avoid rebuilding systems without first auditing them.

The Advanced Embassy Expansion is part of the approved long-term roadmap. Stage 5 implements its **foundation**, not every advanced espionage feature. Named spies, espionage operations, counterintelligence, influence operations and diplomatic incidents remain committed to the broader expansion and must be allocated to subsequent roadmap stages before development.

## Existing engine and implementation findings
- Existing `DiplomacyManager.kt` contains `RelationshipLevel`, `DiplomacyFlags`, `DiplomaticModifiers` and embassy-related effects. Reuse these rather than introduce competing reputation systems.
- Stage 5 branch currently adds persistent war-exhaustion fields, turn progression, war reset, AI peace-pressure integration and unit tests.
- Passing CI is not proof of complete AI simulations, save/load behavior or player acceptance.
- Existing embassy mechanics must be audited before replacing or extending their data model.

## Stage 5 scope and implementation order
1. **Baseline and persistence audit:** trace existing embassy representation, diplomacy modifiers, trade/AI interfaces, save/load serialization and Stage 2–4 treaties; document extension points.
2. **Reputation and trust:** implement bounded, persistent consequences for treaty betrayal, aggression and diplomatic conduct, with decay and AI usage; reuse existing modifiers.
3. **Embassy foundation:** define bilateral embassy states, establishment and closure rules, and named ambassador assignments. Integrate with existing embassy mechanics and persistence.
4. **Diplomatic actions:** integrate denunciations, diplomatic demands and initial ambassador missions into existing player and AI diplomacy/trade interfaces.
5. **Strategic AI:** factor reputation, trust, military strength, embassy access, war duration and war exhaustion into offers, refusals, demands and peace decisions.
6. **Player interface:** expose embassy status, ambassadors, missions and diplomatic consequences on Android without breaking existing diplomacy screens.
7. **Validation:** targeted regression tests, full AI-vs-AI and player-vs-AI simulations, save/load, treaty expiry, defensive intervention, peace negotiations, performance and Android playtesting.

## Later Advanced Embassy Expansion stages
- Named spies and espionage missions
- Intelligence gathering, infiltration and counterintelligence
- Diplomatic influence operations and diplomatic incidents
- Advanced mission consequences and AI strategic responses

These are approved expansion features but **not represented as already implemented** or required to be fully completed in Stage 5. Their exact later-stage allocations remain to be detailed in the overall roadmap.

## Merge gates
- No regressions to locked Stages 2–4.
- All Stage 5 features above implemented and covered by relevant tests.
- Android debug APK, static analysis, unit tests and simulation workflows pass on the final commit.
- Save/load, player/AI scenarios and Android stability verified.
- User explicitly approves merging the Stage 5 PR to master.

## Progress at roadmap reconciliation
- War exhaustion and AI peace integration: implemented with passing CI at commit `1bb5324`.
- Additional diplomatic reputation, embassy foundation, named ambassadors, missions, demands and broader strategic AI: pending.
- Full-game simulations and human gameplay acceptance: pending.
