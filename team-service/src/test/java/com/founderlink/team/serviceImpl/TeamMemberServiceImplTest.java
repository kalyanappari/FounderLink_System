package com.founderlink.team.serviceImpl;

import com.founderlink.team.command.TeamMemberCommandService;
import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.query.TeamMemberQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceImplTest {

    @Mock
    private TeamMemberCommandService commandService;

    @Mock
    private TeamMemberQueryService queryService;

    @InjectMocks
    private TeamMemberServiceImpl teamMemberService;

    @Test
    void joinTeam_ShouldDelegateToCommandService() {
        JoinTeamRequestDto dto = new JoinTeamRequestDto();
        TeamMemberResponseDto response = new TeamMemberResponseDto();
        when(commandService.joinTeam(200L, dto)).thenReturn(response);

        TeamMemberResponseDto result = teamMemberService.joinTeam(200L, dto);

        assertThat(result).isEqualTo(response);
        verify(commandService).joinTeam(200L, dto);
    }

    @Test
    void removeTeamMember_ShouldDelegateToCommandService() {
        doNothing().when(commandService).removeTeamMember(10L, 5L);

        teamMemberService.removeTeamMember(10L, 5L);

        verify(commandService).removeTeamMember(10L, 5L);
    }

    @Test
    void getTeamByStartupId_ShouldDelegateToQueryService() {
        TeamMemberResponseDto response = new TeamMemberResponseDto();
        when(queryService.getTeamByStartupId(100L, 5L, "ROLE_FOUNDER")).thenReturn(List.of(response));

        List<TeamMemberResponseDto> result = teamMemberService.getTeamByStartupId(100L, 5L, "ROLE_FOUNDER");

        assertThat(result).hasSize(1);
        verify(queryService).getTeamByStartupId(100L, 5L, "ROLE_FOUNDER");
    }

    @Test
    void getMemberHistory_ShouldDelegateToQueryService() {
        TeamMemberResponseDto response = new TeamMemberResponseDto();
        when(queryService.getMemberHistory(200L)).thenReturn(List.of(response));

        List<TeamMemberResponseDto> result = teamMemberService.getMemberHistory(200L);

        assertThat(result).hasSize(1);
        verify(queryService).getMemberHistory(200L);
    }

    @Test
    void getActiveMemberRoles_ShouldDelegateToQueryService() {
        TeamMemberResponseDto response = new TeamMemberResponseDto();
        when(queryService.getActiveMemberRoles(200L)).thenReturn(List.of(response));

        List<TeamMemberResponseDto> result = teamMemberService.getActiveMemberRoles(200L);

        assertThat(result).hasSize(1);
        verify(queryService).getActiveMemberRoles(200L);
    }

    @Test
    void isTeamMember_ShouldDelegateToQueryService() {
        when(queryService.isTeamMember(100L, 200L)).thenReturn(true);

        boolean result = teamMemberService.isTeamMember(100L, 200L);

        assertThat(result).isTrue();
        verify(queryService).isTeamMember(100L, 200L);
    }
}
