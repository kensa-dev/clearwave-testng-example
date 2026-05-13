# Clearwave Kensa Example (TestNG)

A showcase project demonstrating [Kensa](https://kensa.dev) — BDD testing for Kotlin & Java — running on **TestNG**.

This is the TestNG counterpart of [clearwave-example](https://github.com/kensa-dev/clearwave-example) (JUnit 6).
Same domain, same Given-When-Then DSL, same per-test tracking-id pattern, same auto-published Kensa report —
only the test runner differs.

The domain is a fictional telecoms provider ("Clearwave") with two services under test:

- **FeasibilityService** — checks whether a broadband service can be delivered to a given address
- **OrderService** — places a broadband order and coordinates with external network and tracking systems

- **System View** — auto-generated component diagram showing the relationships across all services exercised in tests. Available as a top-level page in the report sidebar.

Tests are written using the Kensa Given-When-Then DSL with [http4k](https://http4k.org) stubs standing in for downstream APIs. The HTML report generated from these tests is published as a live example at:

**[kensa-dev.github.io/clearwave-testng-example](https://kensa-dev.github.io/clearwave-testng-example)**

## Running locally

```bash
./gradlew test
```

Runs the http4k-driven `FeasibilityServiceTest` and `OrderServiceTest` (plus the field DSL example and the
two Java equivalents). The report is written to `build/kensa-output`.

To open the report:

```bash
kensa --dir build/kensa-output
```

## TestNG integration

The Kensa lifecycle listener `dev.kensa.testng.KensaTestNgListener` is auto-discovered via `ServiceLoader`,
so the test classes only need to register the project's own listener:

```kotlin
@Listeners(ClearwaveTestNgListener::class)
@UseSetupStrategy(SetupStrategy.Grouped)
abstract class ClearwaveTest : KensaTest, WithKotest
```

`ClearwaveTestNgListener` is an `ISuiteListener` — stubs start in `onStart(suite)` and stop in `onFinish(suite)`.

## Purpose

This project serves two roles:

1. **Showcase** — a realistic example of Kensa tests on TestNG that visitors to [kensa.dev](https://kensa.dev) can explore.
2. **Canary** — run as part of Kensa's CI on every commit to master, building against the latest snapshot to catch regressions early.

## Dependencies

| Library | Role |
|---|---|
| [Kensa](https://kensa.dev) | BDD test framework |
| [http4k](https://http4k.org) | HTTP client & stub server |
| [Kotest](https://kotest.io) | Assertions (Kotlin) |
| [Hamcrest](https://hamcrest.org) | Assertions (Java) |
| [TestNG](https://testng.org) | Test runner |
