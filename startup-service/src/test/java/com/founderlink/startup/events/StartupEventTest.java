package com.founderlink.startup.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartupEventTest {

    @Test
    void testStartupCreatedEvent() {
        StartupCreatedEvent event = new StartupCreatedEvent();
        event.setStartupId(1L);
        event.setFounderId(2L);
        event.setStartupName("Test");
        event.setIndustry("Tech");
        event.setFundingGoal(java.math.BigDecimal.TEN);

        assertThat(event.getStartupId()).isEqualTo(1L);
        assertThat(event.getFounderId()).isEqualTo(2L);
        assertThat(event.getStartupName()).isEqualTo("Test");
        
        StartupCreatedEvent event2 = new StartupCreatedEvent(1L, "Test", 2L, "Tech", java.math.BigDecimal.TEN);
        assertThat(event2.getStartupId()).isEqualTo(1L);
    }

    @Test
    void testStartupDeletedEvent() {
        StartupDeletedEvent event = new StartupDeletedEvent();
        event.setStartupId(1L);
        event.setFounderId(2L);

        assertThat(event.getStartupId()).isEqualTo(1L);
        assertThat(event.getFounderId()).isEqualTo(2L);

        StartupDeletedEvent event2 = new StartupDeletedEvent(1L, 2L);
        assertThat(event2.getStartupId()).isEqualTo(1L);
    }
}
