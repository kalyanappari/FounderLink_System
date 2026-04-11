package com.founderlink.team.command;

import com.founderlink.team.client.StartupServiceClient;
import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.StartupResponseDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.entity.Invitation;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamMember;
import com.founderlink.team.entity.TeamRole;
import com.founderlink.team.events.TeamEventPublisher;
import com.founderlink.team.events.TeamMemberAcceptedEvent;
import com.founderlink.team.exception.*;
import com.founderlink.team.mapper.TeamMemberMapper;
import com.founderlink.team.repository.InvitationRepository;
import com.founderlink.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberCommandServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private StartupServiceClient startupServiceClient;
    @Mock
    private TeamEventPublisher teamEventPublisher;

    @InjectMocks
    private TeamMemberCommandService commandService;

    private Invitation invitation;
    private TeamMember teamMember;
    private TeamMemberResponseDto responseDto;
    private StartupResponseDto startupResponse;
    private JoinTeamRequestDto joinRequest;

    @BeforeEach
    void setUp() {
        invitation = new Invitation();
        invitation.setId(1L);
        invitation.setStartupId(100L);
        invitation.setInvitedUserId(200L);
        invitation.setFounderId(5L);
        invitation.setRole(TeamRole.CTO);
        invitation.setStatus(InvitationStatus.PENDING);

        teamMember = new TeamMember();
        teamMember.setId(10L);
        teamMember.setStartupId(100L);
        teamMember.setUserId(200L);
        teamMember.setIsActive(true);

        responseDto = new TeamMemberResponseDto();
        responseDto.setId(10L);

        startupResponse = new StartupResponseDto();
        startupResponse.setId(100L);
        startupResponse.setFounderId(5L);

        joinRequest = new JoinTeamRequestDto();
        joinRequest.setInvitationId(1L);
    }

    @Test
    void joinTeam_Success() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByStartupIdAndUserIdAndIsActiveTrue(anyLong(), anyLong())).thenReturn(false);
        when(teamMemberRepository.existsByStartupIdAndRoleAndIsActiveTrue(anyLong(), any())).thenReturn(false);
        when(teamMemberRepository.save(any())).thenReturn(teamMember);
        when(teamMemberMapper.toResponseDto(any())).thenReturn(responseDto);

        TeamMemberResponseDto result = commandService.joinTeam(200L, joinRequest);

        assertThat(result).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(teamEventPublisher).publishTeamMemberAcceptedEvent(any(TeamMemberAcceptedEvent.class));
    }

    @Test
    void joinTeam_WrongUser_ThrowsException() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.joinTeam(999L, joinRequest))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void joinTeam_NotPending_ThrowsException() {
        invitation.setStatus(InvitationStatus.REJECTED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.joinTeam(200L, joinRequest))
                .isInstanceOf(InvalidInvitationStatusException.class);
    }

    @Test
    void joinTeam_AlreadyMember_ThrowsException() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByStartupIdAndUserIdAndIsActiveTrue(100L, 200L)).thenReturn(true);

        assertThatThrownBy(() -> commandService.joinTeam(200L, joinRequest))
                .isInstanceOf(AlreadyTeamMemberException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void joinTeam_RoleFilled_ThrowsException() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByStartupIdAndUserIdAndIsActiveTrue(anyLong(), anyLong())).thenReturn(false);
        when(teamMemberRepository.existsByStartupIdAndRoleAndIsActiveTrue(100L, TeamRole.CTO)).thenReturn(true);

        assertThatThrownBy(() -> commandService.joinTeam(200L, joinRequest))
                .isInstanceOf(AlreadyTeamMemberException.class)
                .hasMessageContaining("role is already filled");
    }

    @Test
    void removeTeamMember_Success() {
        when(teamMemberRepository.findById(10L)).thenReturn(Optional.of(teamMember));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);

        commandService.removeTeamMember(10L, 5L);

        assertThat(teamMember.getIsActive()).isFalse();
        assertThat(teamMember.getLeftAt()).isNotNull();
        verify(teamMemberRepository).save(teamMember);
    }

    @Test
    void removeTeamMember_FounderSelfRemove_ThrowsException() {
        teamMember.setUserId(5L); // Team member is the founder
        when(teamMemberRepository.findById(10L)).thenReturn(Optional.of(teamMember));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);

        assertThatThrownBy(() -> commandService.removeTeamMember(10L, 5L))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Founder cannot remove themselves");
    }

    @Test
    void removeTeamMemberFallback_ShouldThrowProperException() {
        TeamMemberNotFoundException tnf = new TeamMemberNotFoundException("Not found");
        assertThatThrownBy(() -> commandService.removeTeamMemberFallback(10L, 5L, tnf)).isEqualTo(tnf);

        RuntimeException generic = new RuntimeException("Generic error");
        assertThatThrownBy(() -> commandService.removeTeamMemberFallback(10L, 5L, generic))
                .isInstanceOf(StartupServiceUnavailableException.class);
    }

    @Test
    void verifyFounderOwnsStartup_Forbidden() {
        startupResponse.setFounderId(99L);
        when(teamMemberRepository.findById(10L)).thenReturn(Optional.of(teamMember));
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        
        assertThatThrownBy(() -> commandService.removeTeamMember(10L, 5L))
                .isInstanceOf(ForbiddenAccessException.class);
    }
}
