package com.bank.accountservice.domain;
import com.bank.accountservice.api.AccountResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AccountTest {
    @Test void statusLifecycleIsIdempotentAndClosedIsTerminal() {
        var now=Instant.parse("2026-10-09T12:00:00Z");
        var a=new Account(1L,"BA123",AccountType.CURRENT,"BAM",now);
        a.changeStatus(AccountStatus.BLOCKED,now.plusSeconds(1));
        assertThat(a.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        a.changeStatus(AccountStatus.ACTIVE,now.plusSeconds(2));
        a.changeStatus(AccountStatus.CLOSED,now.plusSeconds(3));
        a.changeStatus(AccountStatus.CLOSED,now.plusSeconds(4));
        assertThat(a.getUpdatedAt()).isEqualTo(now.plusSeconds(3));
        assertThatThrownBy(()->a.changeStatus(AccountStatus.ACTIVE,now.plusSeconds(5))).isInstanceOf(AccountRuleViolation.class);
        assertThat(a.getCreatedAt()).isEqualTo(now);
    }
    @Test void balancesUseTheCurrencyFractionDigits() {
        for (var code: new String[]{"BAM","JPY","KWD","CLF"}) {
            var a=new Account(1L,"BA123",AccountType.SAVINGS,code,Instant.now());
            var response=AccountResponse.from(a);
            assertThat(response.balance().signum()).isZero();
            assertThat(response.balance().scale()).isEqualTo(java.util.Currency.getInstance(code).getDefaultFractionDigits());
        }
    }
}
