package com.bank.transactionservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
@WebMvcTest(value = FoundationController.class, properties = {"CONFIG_IMPORT=optional:classpath:/no-config-client.yml", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class FoundationControllerTest {
    @Autowired MockMvc mvc;
    @Test void exposesExplicitFoundationStatus() throws Exception {
        mvc.perform(get("/foundation")).andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("transaction-service"))
            .andExpect(jsonPath("$.phase").value("foundation"));
    }
}
