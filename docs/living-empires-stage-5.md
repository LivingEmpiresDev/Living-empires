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

## Embassy and reputation audit — existing extension points
- `DiplomacyFunctions.canOfferEmbassyTo`, `canEstablishEmbassyWith`, `meetsEmbassyRequirementFor` and `removeEmbassies` already manage embassy eligibility and closure.
- `DiplomacyAutomation.offerToEstablishEmbassy` and `wantsToAcceptEmbassy` already handle AI embassy offers and acceptance.
- `TradeEvaluation` and `TradeOfferType.Embassy` already handle embassy trade valuation.
- `DiplomacyManager` already has `EstablishedEmbassy`, `ReceivedEmbassy`, `SharedEmbassies`, denunciation, betrayal and `UnacceptableDemands` modifiers.
- **Implementation decision:** extend the existing embassy/trade/modifier lifecycle rather than create a second embassy state machine. Audit the exact persisted diplomacy state and city-capital-loss behavior before adding ambassador assignments.
- **Important:** existing denunciation and demand mechanics are not equivalent to the proposed expanded reputation and diplomatic mission systems; assess player UI and AI hooks before declaring those features complete.

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


## Pre-simulation integration checkpoint — 19 October 2026
- Master now has `.github/workflows/permanent-regression.yml` with 100-turn AI smoke scenarios for seeds 101, 202 and 303, a 500-turn AI stress scenario for seed 404, turn-trace and war-telemetry validators, JVM tests and Android APK build. Master run 39169316256 reports all five jobs successful.
- **Do not infer Stage 5 simulation coverage from master results:** the Stage 5 branch is behind master and its ambassador/war-exhaustion changes have not been tested by that master run.
- Before claiming Stage 5 simulation acceptance, reconcile master into the Stage 5 branch without overwriting either branch, resolve any diplomacy test/turn-manager conflicts, and execute the permanent regression workflow on the resulting Stage 5 commit.
- Add scenario-specific assertions for ambassador appointment only with an established embassy, stable non-placeholder names, independent bilateral assignments, persistence through save/load, closure and war cleanup, and AI non-overwrite of existing representatives. Existing unit tests do not establish full simulation coverage.
- Preserve Stage 2–4 acceptance as baseline. Require verified AI turn traces and explicit assertions for pact expiry, defensive intervention, war/peace transitions and war exhaustion; a successful turn count alone is not sufficient.
- Continue implementing Stage 5 reputation, diplomatic missions and Android UI independently of the simulator, with targeted tests and ordinary CI for each change.
- Do not merge Stage 5 to master until final branch-specific simulations, Android build, human gameplay acceptance and explicit approval pass.

## Batch 2 verification checkpoint — 19 October 2026
- Trust is directional and derives from persisted betrayal/war modifiers, with existing game-speed-adjusted per-turn decay; no second trust state or decay loop.
- AI trust gates now cover embassy offers, defensive-pact acceptance, friendship and open borders. At trust below 20, AI refuses even if other opinion bonuses are high; other eligibility checks still apply above the threshold.
- Regression tests cover trust boundaries, directional independence, decay, compounded war/treaty betrayal, and refusal cases. The latest branch-wide CI and permanent simulation must complete before declaring this batch verified.
- Broader reputation effects on peace negotiation and war choices require separate design and scenario validation; do not mark them complete by inference from opinion modifiers.
- Batch 3 should implement an ambassador mission as a complete gameplay slice (eligibility, cost, duration, AI, player UI, persistence, and tests) without replacing existing embassy/trade state.

## Streamlined Stage 5 execution plan — 19 October 2026
Use **vertical feature batches** instead of one small commit per subtask:
1. **Embassy gameplay batch:** ambassador lifecycle + AI appointment + player-facing embassy/ambassador view and controls + focused persistence/closure tests. Reuse the existing diplomacy UI and embassy rules; do not build a parallel subsystem.
2. **Diplomatic consequences batch:** reputation/trust modifiers + treaty-betrayal/war/peace hooks + AI valuation + player-facing explanation + scenario tests.
3. **Diplomatic actions batch:** first ambassador mission and diplomatic demands, including cost, eligibility, duration, AI choice, save/load and UI + scenario tests.
4. **Acceptance batch:** all Stage 2–4 regression tests, Stage 5-specific AI scenarios, 100-turn three-seed smoke tests, 500-turn stress, Android build, APK player testing and explicit merge approval.

For each batch, inspect affected files and tests together, implement the gameplay slice and its assertions in one cohesive change where possible, and push once to reduce duplicate CI starts. Investigate failures and repair without requesting permission for ordinary branch changes. The permanent regression and build/test workflows now cancel superseded runs of the same event/branch; preserve the latest completed results. Do not weaken simulation assertions to gain green CI. Avoid cosmetic-only commits unless correcting a verified defect. Never merge Stage 5 into master automatically.
