package com.founderlink.team.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.entity.TeamRole;
import com.founderlink.team.service.TeamMemberService;
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

@WebMvcTest(value = TeamMemberController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ExtendWith(MockitoExtension.class)
class TeamMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeamMemberService teamMemberService;

    @Autowired
    private ObjectMapper objectMapper;

    private TeamMemberResponseDto responseDto;
    private JoinTeamRequestDto joinRequest;

    @BeforeEach
    void setUp() {
        responseDto = new TeamMemberResponseDto();
        responseDto.setId(10L);
        responseDto.setStartupId(100L);
        responseDto.setRole(TeamRole.CTO);

        joinRequest = new JoinTeamRequestDto();
        joinRequest.setInvitationId(1L);
    }

    @Test
    void joinTeam_Success() throws Exception {
        when(teamMemberService.joinTeam(eq(200L), any())).thenReturn(responseDto);

        mockMvc.perform(post("/teams/join")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Successfully joined the team"));
    }

    @Test
    void joinTeam_Forbidden() throws Exception {
        mockMvc.perform(post("/teams/join")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_FOUNDER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTeamByStartupId_Founder_Success() throws Exception {
        when(teamMemberService.getTeamByStartupId(eq(100L), eq(5L), eq("ROLE_FOUNDER")))
                .thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/startup/100")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Team members fetched successfully"));
    }

    @Test
    void getTeamByStartupId_MemberCoFounder_Success() throws Exception {
        when(teamMemberService.isTeamMember(100L, 200L)).thenReturn(true);
        when(teamMemberService.getTeamByStartupId(eq(100L), eq(200L), eq("ROLE_COFOUNDER")))
                .thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/startup/100")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isOk());
    }

    @Test
    void getTeamByStartupId_NonMemberCoFounder_Forbidden() throws Exception {
        when(teamMemberService.isTeamMember(100L, 200L)).thenReturn(false);

        mockMvc.perform(get("/teams/startup/100")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTeamByStartupId_InvalidRole_Forbidden() throws Exception {
        mockMvc.perform(get("/teams/startup/100")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_UNKNOWN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeTeamMember_Success() throws Exception {
        mockMvc.perform(delete("/teams/10")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Team member removed successfully"));
    }

    @Test
    void removeTeamMember_Forbidden() throws Exception {
        mockMvc.perform(delete("/teams/10")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMemberHistory_CoFounder_Success() throws Exception {
        when(teamMemberService.getMemberHistory(200L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/member/history")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isOk());
    }

    @Test
    void getMemberHistory_Admin_Success() throws Exception {
        when(teamMemberService.getMemberHistory(1L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/member/history")
                .header("X-User-Id", 1L)
                .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getMemberHistory_Forbidden() throws Exception {
        mockMvc.perform(get("/teams/member/history")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_FOUNDER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActiveMemberRoles_CoFounder_Success() throws Exception {
        when(teamMemberService.getActiveMemberRoles(200L)).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/teams/member/active")
                .header("X-User-Id", 200L)
                .header("X-User-Role", "ROLE_COFOUNDER"))
                .andExpect(status().isOk());
    }

    @Test
    void getActiveMemberRoles_Forbidden() throws Exception {
        mockMvc.perform(get("/teams/member/active")
                .header("X-User-Id", 5L)
                .header("X-User-Role", "ROLE_INVESTOR"))
                .andExpect(status().isForbidden());
    }
}
