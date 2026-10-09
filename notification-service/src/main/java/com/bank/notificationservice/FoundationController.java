package com.bank.notificationservice;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Infrastructure probe only; business APIs are implemented in later phases. */
@RestController
public class FoundationController {
    @GetMapping("/foundation")
    public Map<String, String> foundation() {
        return Map.of("service", "notification-service", "phase", "foundation");
    }
}
