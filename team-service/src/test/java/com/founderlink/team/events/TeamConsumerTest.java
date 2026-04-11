package com.founderlink.team.events;

import com.founderlink.team.entity.Invitation;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamMember;
import com.founderlink.team.repository.InvitationRepository;
import com.founderlink.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamConsumerTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private StartupDeletedEventConsumer consumer;

    @Test
    void handleStartupDeletedEvent_Success() {
        StartupDeletedEvent event = new StartupDeletedEvent(100L, 5L);
        Invitation pending = new Invitation();
        pending.setId(1L);
        pending.setStatus(InvitationStatus.PENDING);
        
        Invitation accepted = new Invitation();
        accepted.setId(2L);
        accepted.setStatus(InvitationStatus.ACCEPTED);

        TeamMember member = new TeamMember();
        member.setId(10L);
        member.setIsActive(true);

        when(invitationRepository.findByStartupId(100L)).thenReturn(List.of(pending, accepted));
        when(teamMemberRepository.findByStartupIdAndIsActiveTrue(100L)).thenReturn(List.of(member));

        consumer.handleStartupDeletedEvent(event);

        verify(invitationRepository).save(pending);
        verify(invitationRepository, never()).save(accepted);
        verify(teamMemberRepository).save(member);
        
        // Assert state changes
        assert pending.getStatus() == InvitationStatus.CANCELLED;
        assert member.getIsActive() == false;
        assert member.getLeftAt() != null;
    }

    @Test
    void handleStartupDeletedEvent_WithException_ShouldCatch() {
        StartupDeletedEvent event = new StartupDeletedEvent(100L, 5L);
        when(invitationRepository.findByStartupId(anyLong())).thenThrow(new RuntimeException("DB Error"));

        // Should not throw exception out of handleStartupDeletedEvent
        consumer.handleStartupDeletedEvent(event);

        verify(invitationRepository).findByStartupId(100L);
        verifyNoInteractions(teamMemberRepository);
    }
}
