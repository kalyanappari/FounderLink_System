package com.founderlink.notification.query;

import com.founderlink.notification.dto.NotificationResponseDTO;
import com.founderlink.notification.repository.NotificationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.stream.Collectors;
import com.founderlink.notification.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class NotificationQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueryService.class);

    private final NotificationRepository notificationRepository;

    public NotificationQueryService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * QUERY: Get all notifications for a user (newest first).
     * Cache key = userId + page configs.
     */
    @CircuitBreaker(name = "notificationService", fallbackMethod = "getNotificationsByUserFallback")
    @Retry(name = "notificationService")
    @Cacheable(value = "notificationsByUser", key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public PagedResponse<NotificationResponseDTO> getNotificationsByUser(Long userId, Pageable pageable) {
        log.info("QUERY - getNotificationsByUser: userId={} (cache miss, hitting DB)", userId);
        Page<com.founderlink.notification.entity.Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PagedResponse.of(page.map(this::mapToDTO));
    }

    public PagedResponse<NotificationResponseDTO> getNotificationsByUserFallback(Long userId, Pageable pageable, Throwable throwable) {
        log.error("Fallback - getNotificationsByUser. User: {}, Reason: {}", userId, throwable.getMessage());
        return new PagedResponse<>();
    }

    /**
     * QUERY: Get only unread notifications for a user.
     * Cache key = userId + page configs.
     */
    @CircuitBreaker(name = "notificationService", fallbackMethod = "getUnreadNotificationsFallback")
    @Retry(name = "notificationService")
    @Cacheable(value = "unreadNotifications", key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public PagedResponse<NotificationResponseDTO> getUnreadNotifications(Long userId, Pageable pageable) {
        log.info("QUERY - getUnreadNotifications: userId={} (cache miss, hitting DB)", userId);
        Page<com.founderlink.notification.entity.Notification> page = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable);
        return PagedResponse.of(page.map(this::mapToDTO));
    }

    public PagedResponse<NotificationResponseDTO> getUnreadNotificationsFallback(Long userId, Pageable pageable, Throwable throwable) {
        log.error("Fallback - getUnreadNotifications. User: {}, Reason: {}", userId, throwable.getMessage());
        return new PagedResponse<>();
    }

    /**
     * QUERY: Get unread notification integer total.
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    private NotificationResponseDTO mapToDTO(com.founderlink.notification.entity.Notification notification) {
        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
