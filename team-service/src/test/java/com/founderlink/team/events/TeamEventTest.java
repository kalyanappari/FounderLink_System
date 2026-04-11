package com.founderlink.team.events;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TeamEventTest {

    @Test
    void teamInviteEvent_Data_ShouldWork() {
        TeamInviteEvent event = new TeamInviteEvent(1L, 2L, "CTO");
        assertThat(event.getStartupId()).isEqualTo(1L);
        assertThat(event.getInvitedUserId()).isEqualTo(2L);
        assertThat(event.getRole()).isEqualTo("CTO");

        TeamInviteEvent empty = new TeamInviteEvent();
        empty.setStartupId(10L);
        assertThat(empty.getStartupId()).isEqualTo(10L);
    }

    @Test
    void teamMemberAcceptedEvent_Data_ShouldWork() {
        TeamMemberAcceptedEvent event = new TeamMemberAcceptedEvent(100L, 1L, 5L, 200L, "CTO");
        assertThat(event.getInvitationId()).isEqualTo(100L);
        assertThat(event.getStartupId()).isEqualTo(1L);
        assertThat(event.getFounderId()).isEqualTo(5L);
        assertThat(event.getAcceptedUserId()).isEqualTo(200L);
        assertThat(event.getRole()).isEqualTo("CTO");

        TeamMemberAcceptedEvent empty = new TeamMemberAcceptedEvent();
        empty.setAcceptedUserId(300L);
        assertThat(empty.getAcceptedUserId()).isEqualTo(300L);
    }

    @Test
    void teamMemberRejectedEvent_Data_ShouldWork() {
        TeamMemberRejectedEvent event = new TeamMemberRejectedEvent(101L, 1L, 5L, 200L, "CTO");
        assertThat(event.getInvitationId()).isEqualTo(101L);
        assertThat(event.getStartupId()).isEqualTo(1L);
        assertThat(event.getFounderId()).isEqualTo(5L);
        assertThat(event.getRejectedUserId()).isEqualTo(200L);
        assertThat(event.getRole()).isEqualTo("CTO");

        TeamMemberRejectedEvent empty = new TeamMemberRejectedEvent();
        empty.setRejectedUserId(400L);
        assertThat(empty.getRejectedUserId()).isEqualTo(400L);
    }
}
