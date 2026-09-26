package edu.example.course;

import edu.example.course.CourseDelivery.DeliveryRequest;
import edu.example.course.CourseDelivery.EducatorReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseDeliveryController {
    private final CourseDelivery delivery;

    public CourseDeliveryController(CourseDelivery delivery) { this.delivery = delivery; }

    @PostMapping("/course-deliveries")
    public EducatorReport deliver(@RequestBody DeliveryRequest request) { return delivery.deliver(request); }

    @ExceptionHandler(FailureCapture.CaptureRejectedException.class)
    public ResponseEntity<String> captureRejected(FailureCapture.CaptureRejectedException error) {
        HttpStatus status = error.status() >= 400 && error.status() < 500
                ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(error.getMessage());
    }
}
