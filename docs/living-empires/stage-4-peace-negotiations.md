# Stage 4: Advanced Peace Negotiations and Reparations

Status: implementation in progress; this document defines acceptance criteria, not shipped functionality.

## Approved scope
Both human players and AI civilizations may initiate peace offers. Offers may contain white peace, lump-sum gold, gold-per-turn reparations, and city concessions. AI can accept, reject, or counteroffer.

## Existing integration points
- `TradeOfferType` already supports Treaty, Gold, Gold_Per_Turn and City.
- `TradeEvaluation` validates peace treaties and trade values.
- `TradeAutomation.respondToTradeRequests` accepts, declines, and generates counteroffers.
- Existing treaty/trade persistence should be reused rather than adding parallel save-state.

## Implementation sequence
1. Introduce war-context peace valuation without changing ordinary trade evaluation.
2. Generate AI-initiated peace offers with cooldowns, valid available assets and no duplicate pending requests.
3. Implement war-context counteroffers, including bounded gold and city demands.
4. Add UI explanation of who initiated the peace proposal and what ends the war.
5. Add deterministic simulation tests and Android build validation.

## Required tests
- Human and AI can each initiate peace during war, never during peace.
- White peace can be accepted at a stalemate.
- Losing AI is more willing to pay reparations than winning AI, all else equal.
- AI cannot offer gold it does not have or promise unsustainable GPT.
- City concessions cannot eliminate a civilization through invalid trade.
- Both parties end war only after an accepted agreement.
- Pending requests do not spam each turn or endlessly counteroffer.
- Gold, GPT, and city changes persist after save/load.
- Stage 2 and 3 pact/alliance and coordinated-war regression tests remain green.

No merge to master until checks pass and user approves.
