package com.founderlink.investment.serviceImpl;

import com.founderlink.investment.command.InvestmentCommandService;
import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.request.InvestmentStatusUpdateDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.entity.InvestmentStatus;
import com.founderlink.investment.exception.InvestmentNotFoundException;
import com.founderlink.investment.mapper.InvestmentMapper;
import com.founderlink.investment.query.InvestmentQueryService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceImplTest {

    @Mock
    private InvestmentCommandService commandService;
    @Mock
    private InvestmentQueryService queryService;
    @Mock
    private InvestmentRepository investmentRepository;
    @Mock
    private InvestmentMapper investmentMapper;

    @InjectMocks
    private InvestmentServiceImpl service;

    private Investment investment;
    private InvestmentResponseDto responseDto;

    @BeforeEach
    void setUp() {
        investment = new Investment();
        investment.setId(1L);
        investment.setStatus(InvestmentStatus.APPROVED);

        responseDto = new InvestmentResponseDto();
        responseDto.setId(1L);
    }

    @Test
    void createInvestment_Delegates() {
        InvestmentRequestDto dto = new InvestmentRequestDto();
        service.createInvestment(1L, dto);
        verify(commandService).createInvestment(1L, dto);
    }

    @Test
    void updateInvestmentStatus_Delegates() {
        InvestmentStatusUpdateDto dto = new InvestmentStatusUpdateDto();
        service.updateInvestmentStatus(1L, 2L, dto);
        verify(commandService).updateInvestmentStatus(1L, 2L, dto);
    }

    @Test
    void getInvestmentById_Delegates() {
        service.getInvestmentById(1L);
        verify(queryService).getInvestmentById(1L);
    }

    @Test
    void getInvestmentsByStartupId_Delegates() {
        service.getInvestmentsByStartupId(1L, 2L);
        verify(queryService).getInvestmentsByStartupId(1L, 2L);
    }

    @Test
    void getInvestmentsByInvestorId_Delegates() {
        service.getInvestmentsByInvestorId(1L);
        verify(queryService).getInvestmentsByInvestorId(1L);
    }

    @Test
    void markCompletedFromPayment_Success() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentRepository.save(any())).thenReturn(investment);
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markCompletedFromPayment(1L);

        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.COMPLETED);
        verify(investmentRepository).save(any());
    }

    @Test
    void markCompletedFromPayment_AlreadyCompleted() {
        investment.setStatus(InvestmentStatus.COMPLETED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markCompletedFromPayment(1L);

        verify(investmentRepository, never()).save(any());
    }

    @Test
    void markCompletedFromPayment_NotApproved_ShouldNotChange() {
        investment.setStatus(InvestmentStatus.PENDING);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markCompletedFromPayment(1L);

        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.PENDING);
        verify(investmentRepository, never()).save(any());
    }

    @Test
    void markCompletedFromPayment_NotFound() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markCompletedFromPayment(1L))
                .isInstanceOf(InvestmentNotFoundException.class);
    }

    @Test
    void markPaymentFailedFromPayment_Success() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentRepository.save(any())).thenReturn(investment);
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markPaymentFailedFromPayment(1L);

        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.PAYMENT_FAILED);
        verify(investmentRepository).save(any());
    }

    @Test
    void markPaymentFailedFromPayment_AlreadyFailed() {
        investment.setStatus(InvestmentStatus.PAYMENT_FAILED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markPaymentFailedFromPayment(1L);

        verify(investmentRepository, never()).save(any());
    }

    @Test
    void markPaymentFailedFromPayment_NotApproved() {
        investment.setStatus(InvestmentStatus.REJECTED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        service.markPaymentFailedFromPayment(1L);

        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.REJECTED);
        verify(investmentRepository, never()).save(any());
    }

    @Test
    void markPaymentFailedFromPayment_NotFound() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markPaymentFailedFromPayment(1L))
                .isInstanceOf(InvestmentNotFoundException.class);
    }
}
