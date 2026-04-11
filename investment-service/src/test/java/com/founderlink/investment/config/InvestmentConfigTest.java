package com.founderlink.investment.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentConfigTest {

    @Test
    void rabbitMQConfig_Beans_ShouldInitialize() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "investmentQueue", "q1");
        ReflectionTestUtils.setField(config, "startupDeletedQueue", "q2");
        ReflectionTestUtils.setField(config, "paymentCompletedQueue", "q3");
        ReflectionTestUtils.setField(config, "paymentFailedQueue", "q4");
        ReflectionTestUtils.setField(config, "exchange", "ex");
        ReflectionTestUtils.setField(config, "investmentRoutingKey", "r1");
        ReflectionTestUtils.setField(config, "startupDeletedRoutingKey", "r2");
        ReflectionTestUtils.setField(config, "paymentCompletedRoutingKey", "r3");
        ReflectionTestUtils.setField(config, "paymentFailedRoutingKey", "r4");

        assertThat(config.investmentQueue()).isInstanceOf(Queue.class);
        assertThat(config.startupDeletedQueue()).isInstanceOf(Queue.class);
        assertThat(config.paymentCompletedQueue()).isInstanceOf(Queue.class);
        assertThat(config.paymentFailedQueue()).isInstanceOf(Queue.class);
        assertThat(config.founderLinkExchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.investmentCreatedBinding()).isInstanceOf(Binding.class);
        assertThat(config.startupDeletedBinding()).isInstanceOf(Binding.class);
        assertThat(config.paymentCompletedBinding()).isInstanceOf(Binding.class);
        assertThat(config.paymentFailedBinding()).isInstanceOf(Binding.class);
    }

    @Test
    void redisConfig_Bean_ShouldInitialize() {
        RedisConfig config = new RedisConfig();
        org.springframework.data.redis.connection.RedisConnectionFactory mockFactory =
                org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        assertThat(config.cacheManager(mockFactory)).isNotNull();
    }

    @Test
    void redisConfig_ErrorHandler_ShouldNotThrow() {
        RedisConfig config = new RedisConfig();
        org.springframework.cache.interceptor.CacheErrorHandler handler = config.errorHandler();
        org.springframework.cache.Cache mockCache = org.mockito.Mockito.mock(org.springframework.cache.Cache.class);
        RuntimeException ex = new RuntimeException("test");
        // Verify all four handler methods execute without error
        handler.handleCacheGetError(ex, mockCache, "key");
        handler.handleCachePutError(ex, mockCache, "key", "value");
        handler.handleCacheEvictError(ex, mockCache, "key");
        handler.handleCacheClearError(ex, mockCache);
    }

    @Test
    void rabbitMQConfig_MessageConverter_ShouldInitialize() {
        RabbitMQConfig config = new RabbitMQConfig();
        assertThat(config.messageConverter()).isNotNull();
    }

    @Test
    void openApiConfig_Bean_ShouldInitialize() {
        OpenApiConfig config = new OpenApiConfig();
        assertThat(config.customOpenAPI()).isNotNull();
    }

    @Test
    void feignConfig_Beans_ShouldInitialize() {
        FeignConfig config = new FeignConfig();
        assertThat(config.errorDecoder()).isNotNull();
        assertThat(config.feignRetryer()).isNotNull();
        assertThat(config.feignLoggerLevel()).isNotNull();
        assertThat(config.requestOptions()).isNotNull();
    }

    @Test
    void feignErrorDecoder_ShouldHandleAllStatuses() {
        FeignErrorDecoder decoder = new FeignErrorDecoder();
        feign.Response response403 = feign.Response.builder()
                .status(403).reason("Forbidden").request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(decoder.decode("key", response403)).isInstanceOf(com.founderlink.investment.exception.ForbiddenAccessException.class);

        feign.Response response404 = feign.Response.builder()
                .status(404).reason(null).request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(decoder.decode("key", response404)).isInstanceOf(com.founderlink.investment.exception.StartupNotFoundException.class);

        feign.Response response503 = feign.Response.builder()
                .status(503).reason("Unavailable").request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(decoder.decode("key", response503)).isInstanceOf(com.founderlink.investment.exception.StartupServiceUnavailableException.class);

        feign.Response response500 = feign.Response.builder()
                .status(500).reason("Server Error").request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(decoder.decode("key", response500)).isInstanceOf(com.founderlink.investment.exception.StartupServiceServerException.class);

        feign.Response response400 = feign.Response.builder()
                .status(400).reason("Bad Request").request(feign.Request.create(feign.Request.HttpMethod.GET, "/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8)).build();
        assertThat(decoder.decode("key", response400)).isInstanceOf(RuntimeException.class);
    }
}
