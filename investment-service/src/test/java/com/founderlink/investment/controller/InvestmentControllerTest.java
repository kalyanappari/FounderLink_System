package com.founderlink.investment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.investment.dto.request.InvestmentRequestDto;
import com.founderlink.investment.dto.request.InvestmentStatusUpdateDto;
import com.founderlink.investment.dto.response.InvestmentResponseDto;
import com.founderlink.investment.entity.InvestmentStatus;
import com.founderlink.investment.service.InvestmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = InvestmentController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class InvestmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestmentService investmentService;

    @Autowired
    private ObjectMapper objectMapper;

    private InvestmentResponseDto responseDto;
    private InvestmentRequestDto requestDto;

    @BeforeEach
    void setUp() {
        responseDto = new InvestmentResponseDto();
        responseDto.setId(1L);
        responseDto.setStartupId(100L);
        responseDto.setInvestorId(200L);
        responseDto.setAmount(new BigDecimal("1000.00"));
        responseDto.setStatus(InvestmentStatus.PENDING);

        requestDto = new InvestmentRequestDto();
        requestDto.setStartupId(100L);
        requestDto.setAmount(new BigDecimal("1000.00"));
    }

    @Test
    void createInvestment_Success() throws Exception {
        when(investmentService.createInvestment(eq(200L), any())).thenReturn(responseDto);

        mockMvc.perform(post("/investments")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_INVESTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Investment created successfully"));
    }

    @Test
    void createInvestment_Forbidden() throws Exception {
        mockMvc.perform(post("/investments")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_FOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvestmentsByStartupId_Success() throws Exception {
        when(investmentService.getInvestmentsByStartupId(100L, 5L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/investments/startup/100")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Investments fetched successfully"));
    }

    @Test
    void getInvestmentsByStartupId_Admin_Success() throws Exception {
        when(investmentService.getInvestmentsByStartupId(100L, 1L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/investments/startup/100")
                .header("X-User-Id", 1L)
                .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvestmentsByStartupId_Forbidden() throws Exception {
        mockMvc.perform(get("/investments/startup/100")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_INVESTOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvestmentsByInvestorId_Success() throws Exception {
        when(investmentService.getInvestmentsByInvestorId(200L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/investments/investor")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_INVESTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvestmentsByInvestorId_Forbidden() throws Exception {
        mockMvc.perform(get("/investments/investor")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateInvestmentStatus_Success() throws Exception {
        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        when(investmentService.updateInvestmentStatus(eq(1L), eq(5L), any())).thenReturn(responseDto);

        mockMvc.perform(put("/investments/1/status")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Investment status updated successfully"));
    }

    @Test
    void updateInvestmentStatus_Forbidden() throws Exception {
        InvestmentStatusUpdateDto updateDto = new InvestmentStatusUpdateDto();
        updateDto.setStatus(com.founderlink.investment.entity.ManualInvestmentStatus.APPROVED);

        mockMvc.perform(put("/investments/1/status")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_INVESTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvestmentById_Success() throws Exception {
        when(investmentService.getInvestmentById(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/investments/1")
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvestmentById_AsInvestor_Success() throws Exception {
        when(investmentService.getInvestmentById(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/investments/1")
                .header("X-User-Role", "ROLE_INVESTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvestmentById_AsAdmin_Success() throws Exception {
        when(investmentService.getInvestmentById(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/investments/1")
                .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvestmentById_Forbidden() throws Exception {
        mockMvc.perform(get("/investments/1")
                .header("X-User-Role", "ROLE_UNKNOWN"))
                .andExpect(status().isForbidden());
    }
}
