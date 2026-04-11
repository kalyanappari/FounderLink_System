package com.founderlink.team.command;

import com.founderlink.team.client.StartupServiceClient;
import com.founderlink.team.dto.request.InvitationRequestDto;
import com.founderlink.team.dto.response.InvitationResponseDto;
import com.founderlink.team.dto.response.StartupResponseDto;
import com.founderlink.team.entity.Invitation;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamRole;
import com.founderlink.team.events.TeamEventPublisher;
import com.founderlink.team.events.TeamInviteEvent;
import com.founderlink.team.events.TeamMemberRejectedEvent;
import com.founderlink.team.exception.DuplicateInvitationException;
import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.exception.InvalidInvitationStatusException;
import com.founderlink.team.exception.InvitationNotFoundException;
import com.founderlink.team.exception.StartupNotFoundException;
import com.founderlink.team.exception.StartupServiceUnavailableException;
import com.founderlink.team.exception.UnauthorizedAccessException;
import com.founderlink.team.mapper.InvitationMapper;
import com.founderlink.team.repository.InvitationRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationCommandServiceTest {

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private InvitationMapper invitationMapper;
    @Mock
    private TeamEventPublisher eventPublisher;
    @Mock
    private StartupServiceClient startupServiceClient;

    @InjectMocks
    private InvitationCommandService commandService;

    private Invitation invitation;
    private InvitationRequestDto requestDto;
    private InvitationResponseDto responseDto;
    private StartupResponseDto startupResponse;

    @BeforeEach
    void setUp() {
        requestDto = new InvitationRequestDto();
        requestDto.setStartupId(100L);
        requestDto.setInvitedUserId(200L);
        requestDto.setRole(TeamRole.CTO);

        invitation = new Invitation();
        invitation.setId(1L);
        invitation.setStartupId(100L);
        invitation.setInvitedUserId(200L);
        invitation.setFounderId(5L);
        invitation.setRole(TeamRole.CTO);
        invitation.setStatus(InvitationStatus.PENDING);

        responseDto = new InvitationResponseDto();
        responseDto.setId(1L);

        startupResponse = new StartupResponseDto();
        startupResponse.setId(100L);
        startupResponse.setFounderId(5L);
    }

    @Test
    void sendInvitation_Success() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        when(invitationRepository.existsByStartupIdAndInvitedUserIdAndStatus(anyLong(), anyLong(), any())).thenReturn(false);
        when(invitationRepository.existsByStartupIdAndRoleAndStatus(anyLong(), any(), any())).thenReturn(false);
        when(invitationMapper.toEntity(any(), anyLong())).thenReturn(invitation);
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(invitationMapper.toResponseDto(any())).thenReturn(responseDto);

        InvitationResponseDto result = commandService.sendInvitation(5L, requestDto);

        assertThat(result).isNotNull();
        verify(invitationRepository).save(any());
        verify(eventPublisher).publishTeamInviteEvent(any(TeamInviteEvent.class));
    }

    @Test
    void sendInvitation_SelfInvite_ThrowsException() {
        requestDto.setInvitedUserId(5L); // Same as founder
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);

        assertThatThrownBy(() -> commandService.sendInvitation(5L, requestDto))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You cannot invite yourself");
    }

    @Test
    void sendInvitation_DuplicateUser_ThrowsException() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        when(invitationRepository.existsByStartupIdAndInvitedUserIdAndStatus(100L, 200L, InvitationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> commandService.sendInvitation(5L, requestDto))
                .isInstanceOf(DuplicateInvitationException.class)
                .hasMessageContaining("already has a pending invitation");
    }

    @Test
    void sendInvitation_DuplicateRole_ThrowsException() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        when(invitationRepository.existsByStartupIdAndInvitedUserIdAndStatus(anyLong(), anyLong(), any())).thenReturn(false);
        when(invitationRepository.existsByStartupIdAndRoleAndStatus(100L, TeamRole.CTO, InvitationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> commandService.sendInvitation(5L, requestDto))
                .isInstanceOf(DuplicateInvitationException.class)
                .hasMessageContaining("This role already has a pending invitation");
    }

    @Test
    void sendInvitationFallback_ShouldThrowProperException() {
        // Test various throwables in fallback
        StartupNotFoundException snf = new StartupNotFoundException("Not found");
        assertThatThrownBy(() -> commandService.sendInvitationFallback(5L, requestDto, snf)).isEqualTo(snf);

        RuntimeException generic = new RuntimeException("Generic error");
        assertThatThrownBy(() -> commandService.sendInvitationFallback(5L, requestDto, generic))
                .isInstanceOf(StartupServiceUnavailableException.class);
    }

    @Test
    void cancelInvitation_Success() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(invitationMapper.toResponseDto(any())).thenReturn(responseDto);

        InvitationResponseDto result = commandService.cancelInvitation(1L, 5L);

        assertThat(result).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
    }

    @Test
    void cancelInvitation_NotFounder_ThrowsException() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.cancelInvitation(1L, 99L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void cancelInvitation_NotPending_ThrowsException() {
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.cancelInvitation(1L, 5L))
                .isInstanceOf(InvalidInvitationStatusException.class);
    }

    @Test
    void rejectInvitation_Success() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(invitationMapper.toResponseDto(any())).thenReturn(responseDto);

        InvitationResponseDto result = commandService.rejectInvitation(1L, 200L);

        assertThat(result).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REJECTED);
        verify(eventPublisher).publishTeamMemberRejectedEvent(any(TeamMemberRejectedEvent.class));
    }

    @Test
    void rejectInvitation_NotInvitedUser_ThrowsException() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.rejectInvitation(1L, 999L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void rejectInvitation_NotPending_ThrowsException() {
        invitation.setStatus(InvitationStatus.CANCELLED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> commandService.rejectInvitation(1L, 200L))
                .isInstanceOf(InvalidInvitationStatusException.class);
    }

    @Test
    void verifyFounderOwnsStartup_StartupNotFound() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);
        assertThatThrownBy(() -> commandService.sendInvitation(5L, requestDto))
                .isInstanceOf(StartupNotFoundException.class);
    }
}
