package com.founderlink.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceApplicationTest {

    @Test
    void applicationHasCorrectAnnotations() {
        assertThat(NotificationServiceApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class
        )).isTrue();
        assertThat(NotificationServiceApplication.class.isAnnotationPresent(
                org.springframework.cloud.client.discovery.EnableDiscoveryClient.class
        )).isTrue();
        assertThat(NotificationServiceApplication.class.isAnnotationPresent(
                org.springframework.cloud.openfeign.EnableFeignClients.class
        )).isTrue();
        assertThat(NotificationServiceApplication.class.isAnnotationPresent(
                org.springframework.amqp.rabbit.annotation.EnableRabbit.class
        )).isTrue();
    }
}
