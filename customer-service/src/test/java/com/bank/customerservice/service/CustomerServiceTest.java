package com.bank.customerservice.service;

import com.bank.customerservice.api.CustomerRequest;
import com.bank.customerservice.domain.*;
import com.bank.customerservice.repository.CustomerRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock CustomerRepository repository;
    private CustomerService service;
    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    @BeforeEach void setup() { service = new CustomerService(repository, Clock.fixed(now, ZoneOffset.UTC)); }

    @Test void normalizesInformationAndCreatesAnActiveCustomer() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new CustomerRequest(" Dragan ", " Pajic ", " DRAGAN@EXAMPLE.COM ", " +38761123456 "));
        assertThat(result.firstName()).isEqualTo("Dragan");
        assertThat(result.lastName()).isEqualTo("Pajic");
        assertThat(result.email()).isEqualTo("dragan@example.com");
        assertThat(result.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(now);
        verify(repository).saveAndFlush(any(Customer.class));
    }

    @Test void missingCustomerIsNotSilentlyCreated() {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test void deactivationIsLogicalAndIdempotent() {
        var customer = new Customer("A", "B", "a@example.com", "+38761123456", now.minusSeconds(60));
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        service.deactivate(1L);
        service.deactivate(1L);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(customer.getUpdatedAt()).isEqualTo(now);
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test void updatingAnInactiveCustomerDoesNotReactivateOrChangeCreationTime() {
        var created = now.minusSeconds(60);
        var customer = new Customer("A", "B", "a@example.com", "+38761123456", created);
        customer.deactivate(created);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        var result = service.update(1L, new CustomerRequest("New", "Name", "new@example.com", "+38762123456"));
        assertThat(result.status()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(result.createdAt()).isEqualTo(created);
        assertThat(result.updatedAt()).isEqualTo(now);
        verify(repository).flush();
    }
}
