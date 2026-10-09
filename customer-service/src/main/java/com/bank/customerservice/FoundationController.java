package com.bank.customerservice;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Infrastructure probe only; business APIs are implemented in later phases. */
@RestController
public class FoundationController {
    @GetMapping({"/foundation", "/customers/foundation"})
    public Map<String, String> foundation() {
        return Map.of("service", "customer-service", "phase", "foundation");
    }
}
