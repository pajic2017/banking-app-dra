package com.bank.contracts.posting.v1;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PostingCommandV1Test {
    private PostingCommandV1 deposit(String amount,String currency) {
        return new PostingCommandV1(UUID.randomUUID(),PostingType.DEPOSIT,null,1L,new BigDecimal(amount),currency);
    }
    @Test void canonicalHashesIgnoreHarmlessCurrencyAndDecimalFormatting() {
        assertThat(deposit("1.0000"," bam ").instructionHash()).isEqualTo(deposit("1","BAM").instructionHash());
        assertThat(deposit("1","JPY").amount().scale()).isZero();
        assertThat(deposit("1.001","KWD").amount()).isEqualTo(new BigDecimal("1.001"));
        assertThat(deposit("1.0001","CLF").amount()).isEqualTo(new BigDecimal("1.0001"));
    }
    @Test void rejectsZeroNegativeOverflowFractionalAndNonmonetaryAmounts() {
        for (var amount : new String[]{"0","-1","1000000000000000","1.001","1e-1000000"}) {
            assertThatThrownBy(() -> deposit(amount,"BAM")).isInstanceOf(IllegalArgumentException.class);
        }
        for (var currency : new String[]{"ZZZ","XXX","XAU"}) {
            assertThatThrownBy(() -> deposit("1",currency)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> deposit("0.1","JPY")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validatesAccountTopology() {
        assertThatThrownBy(() -> new PostingCommandV1(UUID.randomUUID(),PostingType.TRANSFER,1L,1L,BigDecimal.ONE,"BAM"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PostingCommandV1(UUID.randomUUID(),PostingType.DEPOSIT,1L,2L,BigDecimal.ONE,"BAM"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
