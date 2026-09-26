package edu.example.course;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CourseDelivery {
    private final CaptureReporter capture;
    private final Clock clock;

    @Autowired
    public CourseDelivery(CaptureReporter capture) {
        this(capture, Clock.systemUTC());
    }

    CourseDelivery(CaptureReporter capture, Clock clock) {
        this.capture = capture;
        this.clock = clock;
    }

    public EducatorReport deliver(DeliveryRequest request) {
        boolean overdue = Instant.now(clock).isAfter(request.deadline());
        try {
            // A course agent must have a lesson ready before it can send a learner's next step.
            if (request.lesson().isBlank()) throw new IllegalArgumentException("Lesson is empty");
            return new EducatorReport(request.courseId(), request.learnerId(), "DELIVERED", overdue);
        } catch (IllegalArgumentException failure) {
            capture.capture(request.courseId(), request.learnerId(), request.runId(), "lesson-delivery", failure);
            return new EducatorReport(request.courseId(), request.learnerId(),
                    overdue ? "EDUCATOR_ACTION_REQUIRED" : "RETRY_BEFORE_DEADLINE", overdue);
        }
    }

    public record DeliveryRequest(String runId, String courseId, String learnerId,
                                  Instant deadline, String lesson) {}
    public record EducatorReport(String courseId, String learnerId, String status, boolean overdue) {}

    @FunctionalInterface
    public interface CaptureReporter {
        void capture(String courseId, String learnerId, String runId, String step, Exception cause);
    }
}
