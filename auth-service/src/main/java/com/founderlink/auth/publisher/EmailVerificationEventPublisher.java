package com.founderlink.auth.publisher;

import com.founderlink.auth.config.RabbitMQConfig;
import com.founderlink.auth.dto.EmailVerificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishEmailVerificationEvent(EmailVerificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FOUNDERLINK_EXCHANGE,
                    RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY,
                    event
            );
            log.info("Published email verification event for: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish email verification event for: {}", event.getEmail(), e);
            throw new IllegalStateException("Failed to publish email verification event", e);
        }
    }
}
