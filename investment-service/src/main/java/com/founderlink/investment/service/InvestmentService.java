package com.founderlink.investment.service;

import java.util.List;

import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.request.InvestmentStatusUpdateDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
public interface InvestmentService {

  
    InvestmentResponseDto createInvestment(Long investorId,
                                           InvestmentRequestDto requestDto);

    List<InvestmentResponseDto> getInvestmentsByStartupId(Long startupId,Long founderId);


    List<InvestmentResponseDto> getInvestmentsByInvestorId(Long investorId);

    InvestmentResponseDto updateInvestmentStatus(Long investmentId,Long founderId,
                                                  InvestmentStatusUpdateDto statusUpdateDto);
    
    InvestmentResponseDto getInvestmentById(Long investmentId);

    InvestmentResponseDto markCompletedFromPayment(Long investmentId);

    InvestmentResponseDto markPaymentFailedFromPayment(Long investmentId);

    /**
     * Privacy-safe cross-role count.
     * Returns only the number of COMPLETED investments for a given investor.
     * No investment details or amounts are revealed — safe for FOUNDER access.
     */
    long countCompletedInvestmentsByInvestorId(Long investorId);
}
