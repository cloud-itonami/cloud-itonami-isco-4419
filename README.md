# cloud-itonami-isco-4419

Open Business Blueprint for **ISCO-08 4419**: Clerical Support Workers Not Elsewhere Classified — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — ClericalSupportWorkersAdvisor ⊣
ClericalSupportWorkersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
15 tests / 29 assertions green.

The records-management HARD invariants — an interval floor and an
ordinal scale, neither discretionary:

1. **Retention floor** — a proposed destruction's as-of day must be ≥
   the record's registered retention-expiry-day. Records exist until
   the clock says otherwise, not until convenience says otherwise.
2. **Clearance ordinal** — a proposed access's requester clearance
   must be ordinally ≥ the record's registered required level
   (:public < :internal < :confidential) — access control is ordinal,
   not discretion.

Also HARD: unregistered/foreign record, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-early-destruction` (legal-hold override request), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
