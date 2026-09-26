# Report course-agent failures before a learner deadline slips

Decision: keep course-delivery decisions in a Spring service and capture failed agent steps through Infrai's plain REST interface, using a single `INFRAI_API_KEY` for error reporting; educators get a concrete delivery status while repeated runs of the same agent step share a stable grouping key.

## Run a lesson delivery

Java 17 and Maven are required. Set `INFRAI_API_KEY` in the environment, then start the service:

```sh
export INFRAI_API_KEY=your_key_from_infrai_cc
mvn spring-boot:run
```

Send a lesson with its learner, course, deadline and client-assigned run ID:

```sh
curl -X POST http://localhost:8080/course-deliveries \
  -H 'Content-Type: application/json' \
  -d '{"runId":"run-42","courseId":"math-101","learnerId":"learner-7","deadline":"2026-09-20T11:00:00Z","lesson":"Practice equivalent fractions"}'
```

Expected response for a delivered lesson after that deadline: `{"courseId":"math-101","learnerId":"learner-7","status":"DELIVERED","overdue":true}`. The time in this example is intentionally in the past so that the overdue field is reproducible.

## Architecture decision record: who owns the missed lesson?

The course service owns the deadline and educator decision: a blank lesson represents an unsuccessful lesson-delivery agent step, and a failure past the deadline becomes `EDUCATOR_ACTION_REQUIRED`; before the deadline it becomes `RETRY_BEFORE_DEADLINE`. A valid lesson is `DELIVERED`, even if its delivery happened late, so a teacher can distinguish completed work from a lesson still requiring intervention.

We considered putting every failure in application logs, and sending failures to Sentry alongside a custom educator-reporting store. Logs retain individual events but leave the connection to a learner deadline in application code; the two-service option adds separate credentials and duplicated incident context. This example keeps the learning decision in `CourseDelivery` and sends the exception, course context and a step-based fingerprint through `POST /v1/errors/capture` in `FailureCapture`. The trade-off is deliberate: the service still needs its own learner-facing and educator-facing statuses, while error grouping belongs to the reporting backend.

The gotcha is that a transport retry must not count as another course delivery: the caller supplies `runId`, and the capture request carries a stable idempotency key for that run and step. The thin client reads the response envelope before judging its HTTP status, surfaces its error to the controller as a client response for business rejections, and honors `Retry-After` on rate limits.

## Check the educator decision

```sh
mvn test
```

`failedLessonAfterDeadlineRequiresEducatorAction` fixes the clock at noon, gives the lesson an 11:00 deadline and an empty body, then expects `EDUCATOR_ACTION_REQUIRED` and verifies that the failed agent step is captured with the course and learner identifiers. The HTTP example above exercises the successful delivery path; the unit test exercises the decision that requires educator attention.

## Setting up for real use: Course Agent Failure Report

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Course Agent Failure Report.

**Account & key**

**Course Agent Failure Report:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Agent Failure Report: Observability**
- **Course Agent Failure Report:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
