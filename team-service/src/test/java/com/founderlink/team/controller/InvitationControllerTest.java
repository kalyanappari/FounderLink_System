package com.founderlink.team.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.team.dto.request.InvitationRequestDto;
import com.founderlink.team.dto.response.InvitationResponseDto;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamRole;
import com.founderlink.team.service.InvitationService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = InvitationController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvitationService invitationService;

    @Autowired
    private ObjectMapper objectMapper;

    private InvitationRequestDto requestDto;
    private InvitationResponseDto responseDto;

    @BeforeEach
    void setUp() {
        requestDto = new InvitationRequestDto();
        requestDto.setStartupId(100L);
        requestDto.setInvitedUserId(200L);
        requestDto.setRole(TeamRole.CTO);

        responseDto = new InvitationResponseDto();
        responseDto.setId(1L);
        responseDto.setStartupId(100L);
        responseDto.setStatus(InvitationStatus.PENDING);
    }

    @Test
    void sendInvitation_Success() throws Exception {
        when(invitationService.sendInvitation(eq(5L), any())).thenReturn(responseDto);

        mockMvc.perform(post("/teams/invite")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Invitation sent successfully"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void sendInvitation_Forbidden() throws Exception {
        mockMvc.perform(post("/teams/invite")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_COFOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelInvitation_Success() throws Exception {
        when(invitationService.cancelInvitation(1L, 5L)).thenReturn(responseDto);

        mockMvc.perform(put("/teams/invitations/1/cancel")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitation cancelled successfully"));
    }

    @Test
    void cancelInvitation_Forbidden() throws Exception {
        mockMvc.perform(put("/teams/invitations/1/cancel")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectInvitation_Success() throws Exception {
        when(invitationService.rejectInvitation(1L, 200L)).thenReturn(responseDto);

        mockMvc.perform(put("/teams/invitations/1/reject")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitation rejected successfully"));
    }

    @Test
    void rejectInvitation_Forbidden() throws Exception {
        mockMvc.perform(put("/teams/invitations/1/reject")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvitationsByUserId_Success() throws Exception {
        when(invitationService.getInvitationsByUserId(200L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/invitations/user")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitations fetched successfully"))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void getInvitationsByUserId_Forbidden() throws Exception {
        mockMvc.perform(get("/teams/invitations/user")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvitationsByStartupId_Success() throws Exception {
        when(invitationService.getInvitationsByStartupId(100L, 5L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/invitations/startup/100")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitations fetched successfully"));
    }

    @Test
    void getInvitationsByStartupId_Forbidden() throws Exception {
        mockMvc.perform(get("/teams/invitations/startup/100")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isForbidden());
    }
}
