# Feature: <name>

> Copy this file to `feature-specs/<short-slug>.md` and fill it in. Keep it tight — a spec that needs a table of contents is too long.

## 1. Summary

One paragraph. What is this feature, and what changes once it ships?

## 2. Motivation

Why now? What user pain or business signal is driving this? Reference the persona ([user-personas.md](../user-personas.md)) and any related roadmap entry ([roadmap.md](../roadmap.md)).

## 3. Goals & Non-Goals

**Goals.** Bullet list. Each goal is verifiable.

**Non-goals.** Bullet list. Things you considered and explicitly chose not to do.

## 4. User Stories

- As a `<persona>`, I want to `<action>` so that `<outcome>`.

## 5. Proposed Design

Walk through the change. Reference real package paths, classes, and topics — not generic placeholders.

### 5.1 API changes
New / changed endpoints, request / response shapes. Link to [api-reference.md](../../api/api-reference.md) sections that need updating.

### 5.2 Data model changes
New entities, columns, indexes, Kafka topics, Redis keys. Link to [data-models.md](../../architecture/data-models.md).

### 5.3 Behavior changes
What logic changes inside the system? Mention `RuleEngine`, `FraudDetectionStream`, consumer groups, `WebhookAlertService`, etc., by name.

### 5.4 Configuration
New `application.yml` keys, env vars, feature flags. Default values and what they do.

## 6. Architecture Decision Records to add

If this work commits the system to a non-obvious technical choice, add an ADR entry to [decisions.md](../../architecture/decisions.md). Note them here.

## 7. Test Plan

- Unit tests: which classes, which scenarios.
- Topology tests (`TopologyTestDriver`): which routing branches.
- End-to-end manual verification: list the curl commands and SQL queries.

## 8. Rollout

- Feature flag? (`fraud.features.<name>.enabled`?)
- Default state on first deploy?
- How is this turned off if it misbehaves?

## 9. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| | | | |

## 10. Open Questions

- [ ] ...

## 11. Out of scope / Follow-ups

Things that are explicitly deferred to a later spec.
