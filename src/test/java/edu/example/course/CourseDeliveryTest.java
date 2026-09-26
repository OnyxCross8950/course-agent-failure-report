package edu.example.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CourseDeliveryTest {
    @Test
    void failedLessonAfterDeadlineRequiresEducatorAction() {
        AtomicReference<String> captured = new AtomicReference<>();
        CourseDelivery.CaptureReporter capture = (course, learner, run, step, cause) ->
                captured.set(course + "/" + learner + "/" + run + "/" + step + "/" + cause.getMessage());
        CourseDelivery delivery = new CourseDelivery(capture,
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        CourseDelivery.EducatorReport report = delivery.deliver(new CourseDelivery.DeliveryRequest(
                "run-42", "math-101", "learner-7", Instant.parse("2026-09-20T11:00:00Z"), ""));
        assertEquals("EDUCATOR_ACTION_REQUIRED", report.status());
        assertTrue(report.overdue());
        assertEquals("math-101/learner-7/run-42/lesson-delivery/Lesson is empty", captured.get());
    }
}
