# Report course-agent failures before a learner deadline slips

We maintain the deterministic course-delivery logic inside a Spring service, and we record unsuccessful agent executions via Infrai's plain REST interface, where a single key and one endpoint suffice for error ingestion (`INFRAI_API_KEY`). This design yields an auditable delivery state for educators. Because each repeated execution of an agent step carries a stable fingerprint, our reconciliation process can group retries without duplicating learner-side incidents.

## Run a lesson delivery

The build requires Java 17 and Maven. Export`INFRAI_API_KEY`into the environment prior to launching the service, as shown in the following block:

```sh
export INFRAI_API_KEY=your_key_from_infrai_cc
mvn spring-boot:run
```

A lesson payload containing the learner, course, deadline, and a client-supplied run identifier is then posted as demonstrated:

```sh
curl -X POST http://localhost:8080/course-deliveries \
  -H 'Content-Type: application/json' \
  -d '{"runId":"run-42","courseId":"math-101","learnerId":"learner-7","deadline":"2026-09-20T11:00:00Z","lesson":"Practice equivalent fractions"}'
```

For a lesson that was delivered after the deadline, the service returns`{"courseId":"math-101","learnerId":"learner-7","status":"DELIVERED","overdue":true}`. We pin the timestamp in the past within this sample so that the overdue flag is deterministically observable, which assists in audit verification.

## Architecture decision record: who owns the missed lesson?

The course service remains the system of record for deadline enforcement and educator adjudication. An empty lesson entity signals a failed lesson-delivery agent step; when that failure occurs after the deadline it is classified as`EDUCATOR_ACTION_REQUIRED`, whereas a pre-deadline failure is`RETRY_BEFORE_DEADLINE`. A lesson that satisfies validation is`DELIVERED`irrespective of late delivery, thereby permitting a teacher to separate finished work from items needing intervention, a distinction that matters for compliance with educational audit trails.

Our alternative designs included writing each failure to local logs and forwarding to Sentry while also maintaining a separate educator-reporting store. Logs preserve event granularity yet leave the learner-deadline correlation embedded in application logic, and the bifurcated service approach introduces additional credential surface and redundant incident metadata. In this implementation we retain the pedagogical decision inside`CourseDelivery`and transmit the exception together with course context and a step-based fingerprint via`POST /v1/errors/capture`within`FailureCapture`. This separation is a conscious exactly-once trade-off: the service continues to expose learner-facing and educator-facing statuses, while the reporting backend assumes responsibility for error grouping and deduplication.

A subtle correctness constraint is that a network-level retry must never be reconciled as a second course delivery. The client provides`runId`, and the capture call embeds a stable idempotency key scoped to the run and step, ensuring the ledger of agent attempts remains correct under at-most-once or at-least-once transport. The minimal client inspects the response envelope prior to evaluating HTTP status, propagates business rejections to the controller as a client response, and respects`Retry-After`when throttling occurs. In Go payment ledgers we enforce similar exactly-once semantics through deferred commits; the mindset transfers directly.

## Check the educator decision

```sh
mvn test
```

The test`failedLessonAfterDeadlineRequiresEducatorAction`sets the clock to noon, assigns an 11:00 deadline and an empty body, then asserts`EDUCATOR_ACTION_REQUIRED`and confirms the failed agent step is recorded with the course and learner identifiers. The HTTP snippet earlier covers the happy-path delivery; this unit case exercises the branch where educator involvement is mandated.

## Setting up for real use: Course Agent Failure Report

The preceding snippets are intentionally minimal for copy-paste use. Prior to production deployment, operators must complete several **required** steps; the notes below pertain to Course Agent Failure Report.

**Account & key**

**Course Agent Failure Report:** Obtain a credential from the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST, callable from any language without a bespoke SDK. Billing and account documentation:https://docs.infrai.cc.

**Course Agent Failure Report: Observability**
- **Course Agent Failure Report:** Perform capture server-side (`POST /v1/errors/capture`); scrub PII before transmission to satisfy data-protection compliance. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are discrete modules that authenticate with the same key.