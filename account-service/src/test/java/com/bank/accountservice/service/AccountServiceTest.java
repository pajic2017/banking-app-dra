package com.bank.accountservice.service;
import com.bank.accountservice.api.CreateAccountRequest;
import com.bank.accountservice.domain.AccountType;
import com.bank.accountservice.client.CustomerClient;
import com.bank.accountservice.repository.AccountRepository;
import java.time.Clock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock AccountRepository repository;
    @Mock CustomerClient customers;
    @Mock AccountRegistration registration;
    private AccountService service;
    @BeforeEach void setup() { service=new AccountService(repository,customers,registration,Clock.systemUTC()); }
    @Test void customerIsVerifiedBeforePersistence() {
        var request=new CreateAccountRequest(1L,AccountType.CURRENT," bam ");
        service.create(request);
        var order=inOrder(customers,registration);
        order.verify(customers).requireActive(1L);order.verify(registration).create(request);
        assertThat(request.currency()).isEqualTo("BAM");
    }
    @Test void rejectedCustomerCannotReachPersistence() {
        doThrow(new AccountFailure(HttpStatus.CONFLICT,"Inactive")).when(customers).requireActive(1L);
        assertThatThrownBy(()->service.create(new CreateAccountRequest(1L,AccountType.CURRENT,"BAM"))).isInstanceOf(AccountFailure.class);
        verifyNoInteractions(registration,repository);
    }
    @Test void invalidCurrencyFailsBeforeRemoteLookup() {
        for (var currency:new String[]{"ZZZ","XXX","XAU"}) {
            assertThatThrownBy(()->service.create(new CreateAccountRequest(1L,AccountType.CURRENT,currency))).isInstanceOf(AccountFailure.class);
        }
        verifyNoInteractions(customers,registration,repository);
    }
}
