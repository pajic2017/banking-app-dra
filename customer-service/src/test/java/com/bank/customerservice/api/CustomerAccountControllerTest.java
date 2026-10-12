package com.bank.customerservice.api;
import com.bank.customerservice.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.springframework.context.annotation.Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
@WebMvcTest(value=CustomerAccountController.class,properties={"CONFIG_IMPORT=optional:classpath:/none.yml","spring.cloud.config.enabled=false","eureka.client.enabled=false"})
class CustomerAccountControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerService customers;
    @MockitoBean AccountOverviewClient accounts;
    @Test void returnsDownstreamPageForExistingCustomer() throws Exception {
        when(accounts.get(1,0,20)).thenReturn(new CustomerAccountPage(List.of(),0,20,0,0));
        mvc.perform(get("/customers/1/accounts").header("Authorization","Bearer admin")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        var order=inOrder(customers,accounts);order.verify(customers).get(1);order.verify(accounts).get(1,0,20);
    }
    @Test void missingCustomerDoesNotCallDownstream() throws Exception {
        when(customers.get(1)).thenThrow(new CustomerNotFoundException(1));
        mvc.perform(get("/customers/1/accounts").header("Authorization","Bearer admin")).andExpect(status().isNotFound());verifyNoInteractions(accounts);
    }
    @Test void unavailableAccountServiceReturns503NotEmptyPage() throws Exception {
        when(accounts.get(1,0,20)).thenThrow(new AccountOverviewUnavailable());
        mvc.perform(get("/customers/1/accounts").header("Authorization","Bearer admin")).andExpect(status().isServiceUnavailable());
    }
    @Test void paginationAndIdentifiersAreValidated() throws Exception {
        mvc.perform(get("/customers/1/accounts?size=101").header("Authorization","Bearer admin")).andExpect(status().isBadRequest());
        mvc.perform(get("/customers/0/accounts").header("Authorization","Bearer admin")).andExpect(status().isBadRequest());verifyNoInteractions(customers,accounts);
    }
}
