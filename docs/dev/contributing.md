# Contributing

## Branching

- `main` — stable. Tagged commits represent releases.
- `ReArch` — current working branch as of this doc.
- Feature branches — short-lived, off the active working branch. Name with intent: `add-merchant-rule`, `fix-velocity-ttl`.

## Commit messages

Follow the existing style in `git log`:

```
Add Kafka configuration, transaction serialization, and fraud detection stream
Kafka producer created
Initial commit
```

Imperative, capitalized first word, no trailing period. One commit per logical change. Don't squash unrelated changes together.

## Code style

- **Formatting** — IntelliJ default Java style. No checked-in formatter config; keep diffs minimal.
- **Lombok** — `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Slf4j` are all in play. Don't hand-roll equivalents.
- **Constructor injection** — every Spring component takes its dependencies via the constructor. No field `@Autowired` (the deprecated controller pattern was the one exception and is being phased out).
- **Logging** — `@Slf4j` + parameterized SLF4J calls (`log.info("txn {} scored {}", id, score)`). No string concatenation. No `System.out`.
- **Comments** — write them only when the *why* is non-obvious. Don't restate the code.

## Adding a new rule

1. Implement `Rule` ([scoring/rules/Rule.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/rules/Rule.java)) as a `@Component`.
2. Pick a stable `name()` (uppercase, underscored — e.g. `MERCHANT_BLACKLIST`).
3. Pick a `weight()` that reflects confidence; the score is summed and capped at 100. The risk bands are `<30` LOW, `30..69` MEDIUM, `≥70` HIGH.
4. If the rule is stateful, use the existing Redis key naming convention: `{ruleScope}:{customerId}` with a TTL.
5. **Do not** throw from `evaluate()`. Catch your own exceptions if you need to — `RuleEngine` will catch and log anything you let escape, but explicit handling is clearer.
6. Add a unit test under `src/test/java/.../scoring/rules/` (Mockito for Redis interactions; pure JUnit for stateless rules).

The engine picks the rule up automatically via `List<Rule>` injection — no registration needed.

## Adding a new endpoint

1. Add to [`TransactionController`](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java) or create a new `@RestController` for a new resource.
2. Validate inputs with Jakarta validation (`@Valid`, `@NotBlank`, `@Positive`, …).
3. Update [api-reference.md](../api/api-reference.md) — the doc is hand-maintained, the OpenAPI JSON is generated.

## Adding a new Kafka topic / consumer

1. Declare the topic in [`KafkaConfig`](../../src/main/java/com/vishavbangotra/fraud_detection_system/config/KafkaConfig.java) with a `NewTopic` bean and a public constant.
2. If the value type is new, add a Serializer + Deserializer + Serde under `serdes/`. Always go through [`JsonMapper.create()`](../../src/main/java/com/vishavbangotra/fraud_detection_system/serdes/JsonMapper.java) so `Instant` round-trips.
3. For sink-style consumers, add a `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` bean (see [`ScoredTransactionConsumerConfig`](../../src/main/java/com/vishavbangotra/fraud_detection_system/consumers/ScoredTransactionConsumerConfig.java) as a template). Reference it from `@KafkaListener(containerFactory = ...)`.
4. Pick a clear consumer `groupId` — independent groups stay independent (see ADR-004).

## Tests

- Required: at least one unit test per new rule, controller path, or service method with non-trivial branching.
- Strongly preferred: a topology test for any change to [`FraudDetectionStream`](../../src/main/java/com/vishavbangotra/fraud_detection_system/streams/FraudDetectionStream.java). The pattern is in [FraudDetectionStreamTest](../../src/test/java/com/vishavbangotra/fraud_detection_system/streams/FraudDetectionStreamTest.java).
- See [testing-strategy.md](testing-strategy.md) for the full picture.

Run before pushing:

```bash
./mvnw clean verify
```

## Pull request checklist

Before opening a PR:

- [ ] `./mvnw clean verify` passes locally
- [ ] New behavior has tests
- [ ] If you added a topic / endpoint / config key, the relevant doc under `docs/` is updated in the same PR
- [ ] If the change locks in a non-obvious technical choice, append an ADR in [decisions.md](../architecture/decisions.md)
- [ ] Commit messages follow the imperative style above

## What not to do

- Don't introduce a new `ObjectMapper` — go through `JsonMapper`.
- Don't catch exceptions inside Kafka listeners and rethrow — that triggers re-delivery and double-writes (see ADR-006).
- Don't use `ddl-auto: create` or `create-drop` — destroys data on restart.
- Don't reach for new dependencies casually. Run `./mvnw dependency:tree` before / after.
- Don't leave commented-out code.
