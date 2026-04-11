package com.founderlink.team.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TeamEventPublisher publisher;

    private static final String EXCHANGE = "test.exchange";
    private static final String INVITE_RK = "invite.rk";
    private static final String ACC_RK = "acc.rk";
    private static final String REJ_RK = "rej.rk";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(publisher, "teamInviteRoutingKey", INVITE_RK);
        ReflectionTestUtils.setField(publisher, "teamAcceptedRoutingKey", ACC_RK);
        ReflectionTestUtils.setField(publisher, "teamRejectedRoutingKey", REJ_RK);
    }

    @Test
    void publishTeamInviteEvent_Success() {
        TeamInviteEvent event = new TeamInviteEvent(1L, 200L, "CTO");
        publisher.publishTeamInviteEvent(event);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(INVITE_RK), eq(event));
    }

    @Test
    void publishTeamInviteEvent_Failure() {
        doThrow(new RuntimeException("AMQP error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishTeamInviteEvent(new TeamInviteEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void publishTeamMemberAcceptedEvent_Success() {
        TeamMemberAcceptedEvent event = new TeamMemberAcceptedEvent(1L, 100L, 5L, 200L, "CTO");
        publisher.publishTeamMemberAcceptedEvent(event);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ACC_RK), eq(event));
    }

    @Test
    void publishTeamMemberAcceptedEvent_Failure() {
        doThrow(new RuntimeException("AMQP error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishTeamMemberAcceptedEvent(new TeamMemberAcceptedEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void publishTeamMemberRejectedEvent_Success() {
        TeamMemberRejectedEvent event = new TeamMemberRejectedEvent(1L, 100L, 5L, 200L, "CTO");
        publisher.publishTeamMemberRejectedEvent(event);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(REJ_RK), eq(event));
    }

    @Test
    void publishTeamMemberRejectedEvent_Failure() {
        doThrow(new RuntimeException("AMQP error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishTeamMemberRejectedEvent(new TeamMemberRejectedEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish");
    }
}
