package com.founderlink.auth.publisher;

import com.founderlink.auth.config.RabbitMQConfig;
import com.founderlink.auth.dto.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserRegisteredEvent(UserRegisteredEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FOUNDERLINK_EXCHANGE,
                    RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                    event
            );
            log.info("Published user registered event for email: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish user registered event for email: {}", event.getEmail(), e);
        }
    }
}