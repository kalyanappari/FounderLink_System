package com.founderlink.investment.mapper;

import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.entity.InvestmentStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class InvestmentMapperTest {

    private final InvestmentMapper mapper = new InvestmentMapper();

    @Test
    void toEntity_ShouldMapCorrectly() {
        InvestmentRequestDto requestDto = new InvestmentRequestDto();
        requestDto.setStartupId(100L);
        requestDto.setAmount(new BigDecimal("10000.00"));

        Investment entity = mapper.toEntity(requestDto, 200L);

        assertThat(entity.getStartupId()).isEqualTo(100L);
        assertThat(entity.getInvestorId()).isEqualTo(200L);
        assertThat(entity.getAmount()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    void toResponseDto_ShouldMapCorrectly() {
        Investment entity = new Investment();
        entity.setId(1L);
        entity.setStartupId(100L);
        entity.setInvestorId(200L);
        entity.setAmount(new BigDecimal("50000.00"));
        entity.setStatus(InvestmentStatus.APPROVED);

        InvestmentResponseDto responseDto = mapper.toResponseDto(entity);

        assertThat(responseDto.getId()).isEqualTo(1L);
        assertThat(responseDto.getStartupId()).isEqualTo(100L);
        assertThat(responseDto.getInvestorId()).isEqualTo(200L);
        assertThat(responseDto.getAmount()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(responseDto.getStatus()).isEqualTo(InvestmentStatus.APPROVED);
    }
}
