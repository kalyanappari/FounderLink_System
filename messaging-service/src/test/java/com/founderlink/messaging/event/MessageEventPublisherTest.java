package com.founderlink.messaging.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private MessageEventPublisher messageEventPublisher;

    @Test
    @DisplayName("publishMessageSent - publishes expected map payload")
    void publishMessageSent_PublishesMap() {
        ReflectionTestUtils.setField(messageEventPublisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(messageEventPublisher, "routingKey", "test.routing.key");

        messageEventPublisher.publishMessageSent(1L, 100L, 200L, "SenderName");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);

        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.routing.key"), eventCaptor.capture());

        Map<String, Object> event = eventCaptor.getValue();
        assertThat(event).containsEntry("messageId", 1L);
        assertThat(event).containsEntry("senderId", 100L);
        assertThat(event).containsEntry("receiverId", 200L);
        assertThat(event).containsEntry("senderName", "SenderName");
    }
}
