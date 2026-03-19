package com.founderlink.investment.serviceImpl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.founderlink.investment.client.StartupServiceClient;
import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.request.InvestmentStatusUpdateDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.dto.response.StartupResponseDto;
import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.entity.InvestmentStatus;
import com.founderlink.investment.events.InvestmentCreatedEvent;
import com.founderlink.investment.events.InvestmentEventPublisher;
import com.founderlink.investment.exception.DuplicateInvestmentException;
import com.founderlink.investment.exception.ForbiddenAccessException;
import com.founderlink.investment.exception.InvalidStatusTransitionException;
import com.founderlink.investment.exception.InvestmentNotFoundException;
import com.founderlink.investment.exception.StartupNotFoundException;
import com.founderlink.investment.mapper.InvestmentMapper;
import com.founderlink.investment.repository.InvestmentRepository;
import com.founderlink.investment.service.InvestmentService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class InvestmentServiceImpl implements InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentEventPublisher eventPublisher;
    private final InvestmentMapper investmentMapper;
    private final StartupServiceClient startupServiceClient;

    @Override
    public InvestmentResponseDto createInvestment(Long investorId,
                                                   InvestmentRequestDto requestDto) {
    	
    	verifyStartupExists(requestDto.getStartupId());

        // Check duplicate PENDING investment only
        if (investmentRepository
                .existsByStartupIdAndInvestorIdAndStatus(
                        requestDto.getStartupId(),
                        investorId,
                        InvestmentStatus.PENDING)) {
            throw new DuplicateInvestmentException(
                    "You have already invested in this startup");
        }

        Investment investment = investmentMapper
                .toEntity(requestDto, investorId);

        Investment savedInvestment = investmentRepository
                .save(investment);

        InvestmentCreatedEvent event = new InvestmentCreatedEvent(
                savedInvestment.getStartupId(),
                savedInvestment.getInvestorId(),
                savedInvestment.getAmount()
        );
        eventPublisher.publishInvestmentCreatedEvent(event);

        return investmentMapper.toResponseDto(savedInvestment);
    }


    @Override
    public List<InvestmentResponseDto> getInvestmentsByStartupId(Long startupId,Long founderId) {
    	
    	verifyFounderOwnsStartup(
                startupId, founderId); 

        List<Investment> investments = investmentRepository
                .findByStartupId(startupId);

        return investments.stream()
                .map(investmentMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<InvestmentResponseDto> getInvestmentsByInvestorId(Long investorId) {

        List<Investment> investments = investmentRepository
                .findByInvestorId(investorId);

        return investments.stream()
                .map(investmentMapper::toResponseDto)
                .collect(Collectors.toList());
    }


    @Override
    public InvestmentResponseDto updateInvestmentStatus(Long investmentId,Long founderId,
                                                         InvestmentStatusUpdateDto statusUpdateDto) {

        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new InvestmentNotFoundException(
                        "Investment not found with id: " + investmentId));
        
        verifyFounderOwnsStartup(investment.getStartupId(),founderId);

        validateStatusTransition(investment.getStatus(),
                statusUpdateDto.getStatus());

        investment.setStatus(statusUpdateDto.getStatus());

        Investment updatedInvestment = investmentRepository.save(investment);

        return investmentMapper.toResponseDto(updatedInvestment);
    }

    @Override
    public InvestmentResponseDto getInvestmentById(Long investmentId) {

        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new InvestmentNotFoundException(
                        "Investment not found with id: " + investmentId));

        return investmentMapper.toResponseDto(investment);
    }
    
    private void validateStatusTransition(InvestmentStatus currentStatus,
            InvestmentStatus newStatus) {

		if (currentStatus == InvestmentStatus.COMPLETED) {
			throw new InvalidStatusTransitionException(
			"Cannot update a COMPLETED investment");
		}
		
		if (currentStatus == InvestmentStatus.REJECTED) {
			throw new InvalidStatusTransitionException(
			"Cannot update a REJECTED investment");
		}
		
		if (newStatus == InvestmentStatus.COMPLETED
		&& currentStatus != InvestmentStatus.APPROVED) {
			throw new InvalidStatusTransitionException(
			"Investment must be APPROVED before marking COMPLETED");
		}
		
    }
    
    public void verifyFounderOwnsStartup(
            Long startupId,
            Long founderId) {

        // Call Startup Service
        StartupResponseDto startup = startupServiceClient
                .getStartupById(startupId);

        // Startup not found
        if (startup == null) {
            throw new StartupNotFoundException(
                    "Startup not found with id: " + startupId);
        }

        // Founder does not own startup
        if (!startup.getFounderId().equals(founderId)) {
            throw new ForbiddenAccessException(
                    "You are not authorized to " +
                    "perform this action on this startup");
        }
    }
    
    public void verifyStartupExists(Long startupId) {

        StartupResponseDto startup = startupServiceClient
                .getStartupById(startupId);

        if (startup == null) {
            throw new StartupNotFoundException(
                    "Startup not found with id: " + startupId);
        }
    }
    
}