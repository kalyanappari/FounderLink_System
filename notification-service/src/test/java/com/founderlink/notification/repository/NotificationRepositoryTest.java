package com.founderlink.notification.repository;

import com.founderlink.notification.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        notificationRepository.flush();

        // User 100: 2 read, 1 unread
        // @PrePersist sets read=false on initial save, so we save first then mark as read
        Notification n1 = new Notification();
        n1.setUserId(100L);
        n1.setType("STARTUP_CREATED");
        n1.setMessage("New startup created in Tech industry");
        n1 = notificationRepository.save(n1);
        n1.setRead(true);
        notificationRepository.save(n1);

        Notification n2 = new Notification();
        n2.setUserId(100L);
        n2.setType("INVESTMENT_CREATED");
        n2.setMessage("New investment of $50000");
        n2 = notificationRepository.save(n2);
        n2.setRead(true);
        notificationRepository.save(n2);

        Notification n3 = new Notification();
        n3.setUserId(100L);
        n3.setType("TEAM_INVITE_SENT");
        n3.setMessage("You have been invited to join startup #1 as CTO");
        notificationRepository.save(n3); // stays unread (@PrePersist default)

        // User 200: 1 unread
        Notification n4 = new Notification();
        n4.setUserId(200L);
        n4.setType("STARTUP_CREATED");
        n4.setMessage("New startup created in Health industry");
        notificationRepository.save(n4); // stays unread (@PrePersist default)
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc - returns all notifications for user")
    void findByUserId_ReturnsAll() {
        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(100L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(n -> n.getUserId().equals(100L));
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc - returns empty for unknown user")
    void findByUserId_EmptyForUnknown() {
        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(999L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndReadFalseOrderByCreatedAtDesc - returns only unread")
    void findUnread_ReturnsOnlyUnread() {
        Page<Notification> result = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(100L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("TEAM_INVITE_SENT");
        assertThat(result.getContent().get(0).isRead()).isFalse();
    }

    @Test
    @DisplayName("findByUserIdAndReadFalseOrderByCreatedAtDesc - returns empty when all read")
    void findUnread_EmptyWhenAllRead() {
        // Mark all as read for user 100
        notificationRepository.findByUserIdOrderByCreatedAtDesc(100L, PageRequest.of(0, 10)).forEach(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });

        Page<Notification> result = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(100L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndReadFalseOrderByCreatedAtDesc - different user has own unread")
    void findUnread_IsolatedPerUser() {
        Page<Notification> user200Unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(200L, PageRequest.of(0, 10));

        assertThat(user200Unread.getContent()).hasSize(1);
        assertThat(user200Unread.getContent().get(0).getUserId()).isEqualTo(200L);
    }
}
