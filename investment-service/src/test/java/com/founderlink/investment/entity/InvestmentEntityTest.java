package com.founderlink.investment.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentEntityTest {

    @Test
    void testInvestmentEntity() {
        Investment investment = new Investment();
        investment.setId(1L);
        investment.setStartupId(10L);
        investment.setInvestorId(20L);
        investment.setAmount(BigDecimal.valueOf(5000));
        investment.setStatus(InvestmentStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        investment.setCreatedAt(now);

        assertThat(investment.getId()).isEqualTo(1L);
        assertThat(investment.getStartupId()).isEqualTo(10L);
        assertThat(investment.getInvestorId()).isEqualTo(20L);
        assertThat(investment.getAmount()).isEqualTo(BigDecimal.valueOf(5000));
        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.PENDING);
        assertThat(investment.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void testOnCreate() {
        Investment investment = new Investment();
        investment.onCreate();
        
        assertThat(investment.getCreatedAt()).isNotNull();
        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.PENDING);
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Investment investment = new Investment(1L, 10L, 20L, BigDecimal.valueOf(100), InvestmentStatus.APPROVED, now);
        assertThat(investment.getId()).isEqualTo(1L);
    }
}
