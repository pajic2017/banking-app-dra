package com.bank.authservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value=IdentityController.class,properties={"CONFIG_IMPORT=optional:classpath:/none.yml","spring.cloud.config.enabled=false","eureka.client.enabled=false"})
@Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
class IdentityControllerTest {
    @Autowired MockMvc mvc;
    @Test void identityRequiresAuthentication() throws Exception { mvc.perform(get("/me")).andExpect(status().isUnauthorized()); }
    @Test void verifiedIdentityReportsItsOwnSubjectRolesAndCustomerBinding() throws Exception {
        mvc.perform(get("/me").header("Authorization","Bearer alice")).andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("alice"))
            .andExpect(jsonPath("$.customerId").value("1")).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
    }
    @Test void serviceAndRolelessIdentitiesCannotUsePublicIdentityApi() throws Exception {
        for(String token:new String[]{"account-service","roleless"})mvc.perform(get("/me").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void publicMetadataDescribesPkceWithoutSecrets() throws Exception {
        mvc.perform(get("/oidc")).andExpect(status().isOk()).andExpect(jsonPath("$.clientId").value("banking-local")).andExpect(jsonPath("$.pkce").value("S256"))
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
}
