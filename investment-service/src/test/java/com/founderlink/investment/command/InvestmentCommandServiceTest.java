package com.founderlink.investment.command;

import com.founderlink.investment.client.StartupServiceClient;
import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.request.InvestmentStatusUpdateDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.dto.response.StartupResponseDto;
import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.entity.InvestmentStatus;
import com.founderlink.investment.events.InvestmentEventPublisher;
import com.founderlink.investment.exception.*;
import com.founderlink.investment.mapper.InvestmentMapper;
import com.founderlink.investment.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentCommandServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;
    @Mock
    private InvestmentEventPublisher eventPublisher;
    @Mock
    private InvestmentMapper investmentMapper;
    @Mock
    private StartupServiceClient startupServiceClient;

    @InjectMocks
    private InvestmentCommandService commandService;

    private Investment investment;
    private InvestmentRequestDto requestDto;
    private StartupResponseDto startup;
    private InvestmentResponseDto responseDto;

    @BeforeEach
    void setUp() {
        investment = new Investment();
        investment.setId(1L);
        investment.setStartupId(100L);
        investment.setInvestorId(200L);
        investment.setAmount(new BigDecimal("1000.00"));
        investment.setStatus(InvestmentStatus.PENDING);

        requestDto = new InvestmentRequestDto();
        requestDto.setStartupId(100L);
        requestDto.setAmount(new BigDecimal("1000.00"));

        startup = new StartupResponseDto();
        startup.setId(100L);
        startup.setFounderId(5L);

        responseDto = new InvestmentResponseDto();
        responseDto.setId(1L);
    }

    @Test
    void createInvestment_Success() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        when(investmentRepository.existsByStartupIdAndInvestorIdAndStatus(100L, 200L, InvestmentStatus.PENDING))
                .thenReturn(false);
        when(investmentMapper.toEntity(requestDto, 200L)).thenReturn(investment);
        when(investmentRepository.save(any())).thenReturn(investment);
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        InvestmentResponseDto result = commandService.createInvestment(200L, requestDto);

        assertThat(result).isNotNull();
        verify(eventPublisher).publishInvestmentCreatedEvent(any());
    }

    @Test
    void createInvestment_StartupNotFound() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);
        assertThatThrownBy(() -> commandService.createInvestment(200L, requestDto))
                .isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void createInvestment_Duplicate() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        when(investmentRepository.existsByStartupIdAndInvestorIdAndStatus(100L, 200L, InvestmentStatus.PENDING))
                .thenReturn(true);
        assertThatThrownBy(() -> commandService.createInvestment(200L, requestDto))
                .isInstanceOf(DuplicateInvestmentException.class);
    }

    @Test
    void createInvestmentFallback_BusinessEx() {
        StartupNotFoundException ex = new StartupNotFoundException("NF");
        assertThatThrownBy(() -> commandService.createInvestmentFallback(200L, requestDto, ex))
                .isEqualTo(ex);
    }

    @Test
    void createInvestmentFallback_InfraEx() {
        RuntimeException ex = new RuntimeException("DB Error");
        assertThatThrownBy(() -> commandService.createInvestmentFallback(200L, requestDto, ex))
                .isInstanceOf(StartupServiceUnavailableException.class);
    }

    @Test
    void updateInvestmentStatus_Approved_Success() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        when(investmentRepository.save(any())).thenReturn(investment);
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        commandService.updateInvestmentStatus(1L, 5L, updateDto);

        assertThat(investment.getStatus()).isEqualTo(InvestmentStatus.APPROVED);
        verify(eventPublisher).publishInvestmentApprovedEvent(any());
    }

    @Test
    void updateInvestmentStatus_Rejected_Success() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);
        when(investmentRepository.save(any())).thenReturn(investment);
        when(investmentMapper.toResponseDto(investment)).thenReturn(responseDto);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.REJECTED);

        commandService.updateInvestmentStatus(1L, 5L, updateDto);

        verify(eventPublisher).publishInvestmentRejectedEvent(any());
    }

    @Test
    void updateInvestmentStatus_Forbidden() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup); // startup founder is 5L

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 99L, updateDto))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void updateInvestmentStatus_InvalidTransition_Completed() {
        investment.setStatus(InvestmentStatus.COMPLETED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 5L, updateDto))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateInvestmentStatus_InvalidTransition_Rejected() {
        investment.setStatus(InvestmentStatus.REJECTED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 5L, updateDto))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateInvestmentStatus_InvalidTransition_StartupClosed() {
        investment.setStatus(InvestmentStatus.STARTUP_CLOSED);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 5L, updateDto))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateInvestmentStatus_InvalidTransition_Sequencing() {
        // Must be APPROVED before COMPLETED
        investment.setStatus(InvestmentStatus.PENDING);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startup);

        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.COMPLETED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 5L, updateDto))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateInvestmentStatusFallback_BusinessEx() {
        InvestmentNotFoundException ex = new InvestmentNotFoundException("NF");
        assertThatThrownBy(() -> commandService.updateInvestmentStatusFallback(1L, 5L, null, ex))
                .isEqualTo(ex);
    }

    @Test
    void createInvestmentFallback_ForbiddenAccessEx_ShouldRethrow() {
        ForbiddenAccessException ex = new ForbiddenAccessException("Forbidden");
        assertThatThrownBy(() -> commandService.createInvestmentFallback(200L, requestDto, ex))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void createInvestmentFallback_DuplicateInvestmentEx_ShouldRethrow() {
        DuplicateInvestmentException ex = new DuplicateInvestmentException("Dup");
        assertThatThrownBy(() -> commandService.createInvestmentFallback(200L, requestDto, ex))
                .isInstanceOf(DuplicateInvestmentException.class);
    }

    @Test
    void updateInvestmentStatusFallback_ForbiddenEx_ShouldRethrow() {
        ForbiddenAccessException ex = new ForbiddenAccessException("Forbidden");
        assertThatThrownBy(() -> commandService.updateInvestmentStatusFallback(1L, 5L, null, ex))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void updateInvestmentStatus_StartupNotFoundInVerify() {
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(investment));
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);
        
        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        assertThatThrownBy(() -> commandService.updateInvestmentStatus(1L, 5L, updateDto))
                .isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void updateInvestmentStatusFallback_InfraEx() {
        RuntimeException ex = new RuntimeException("DB Error");
        assertThatThrownBy(() -> commandService.updateInvestmentStatusFallback(1L, 5L, null, ex))
                .isInstanceOf(StartupServiceUnavailableException.class);
    }
}
