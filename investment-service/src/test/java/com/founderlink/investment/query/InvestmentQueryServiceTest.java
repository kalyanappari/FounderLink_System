package com.founderlink.investment.query;

import com.founderlink.investment.client.StartupServiceClient;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.dto.response.StartupResponseDto;
import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.exception.ForbiddenAccessException;
import com.founderlink.investment.exception.InvestmentNotFoundException;
import com.founderlink.investment.exception.StartupNotFoundException;
import com.founderlink.investment.exception.StartupServiceUnavailableException;
import com.founderlink.investment.mapper.InvestmentMapper;
import com.founderlink.investment.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentQueryServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private InvestmentMapper investmentMapper;

    @Mock
    private StartupServiceClient startupServiceClient;

    @InjectMocks
    private InvestmentQueryService queryService;

    private Investment investment;
    private InvestmentResponseDto responseDto;
    private StartupResponseDto startup;

    @BeforeEach
    void setUp() {
        investment = new Investment();
        investment.setId(1L);
        investment.setStartupId(100L);
        investment.setInvestorId(200L);

        responseDto = new InvestmentResponseDto();
        responseDto.setId(1L);

        startup = new StartupResponseDto();
        startup.setId(100L);
        startup.setFounderId(5L);
    }

    @Test
    void getInvestmentById_Success() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        InvestmentResponseDto result = queryService.getInvestmentById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getInvestmentById_NotFound() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> queryService.getInvestmentById(1L))
                .isInstanceOf(InvestmentNotFoundException.class);
    }

    @Test
    void getInvestmentsByStartupId_Success() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        when(investmentRepository.findByStartupId(100L)).thenReturn(List.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        List<InvestmentResponseDto> result = queryService.getInvestmentsByStartupId(100L, 5L);

        assertThat(result).hasSize(1);
        verify(startupServiceClient).getStartupById(100L);
    }

    @Test
    void getInvestmentsByStartupId_Forbidden() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        assertThatThrownBy(() -> queryService.getInvestmentsByStartupId(100L, 99L))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getInvestmentsByStartupId_StartupNotFound() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);
        assertThatThrownBy(() -> queryService.getInvestmentsByStartupId(100L, 5L))
                .isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void getInvestmentsByStartupIdFallback_BusinessEx() {
        StartupNotFoundException ex = new StartupNotFoundException("NF");
        assertThatThrownBy(() -> queryService.getInvestmentsByStartupIdFallback(100L, 5L, ex))
                .isEqualTo(ex);
    }

    @Test
    void getInvestmentsByStartupIdFallback_ForbiddenEx() {
        ForbiddenAccessException ex = new ForbiddenAccessException("Forbidden");
        assertThatThrownBy(() -> queryService.getInvestmentsByStartupIdFallback(100L, 5L, ex))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getInvestmentsByStartupIdFallback_InfraEx() {
        RuntimeException ex = new RuntimeException("Generic Connection Error");
        assertThatThrownBy(() -> queryService.getInvestmentsByStartupIdFallback(100L, 5L, ex))
                .isInstanceOf(StartupServiceUnavailableException.class);
    }

    @Test
    void getInvestmentsByInvestorId_Success() {
        when(investmentRepository.findByInvestorId(200L)).thenReturn(List.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        List<InvestmentResponseDto> result = queryService.getInvestmentsByInvestorId(200L);

        assertThat(result).hasSize(1);
    }
}
