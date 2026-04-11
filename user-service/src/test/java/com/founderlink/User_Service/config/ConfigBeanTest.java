package com.founderlink.User_Service.config;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;
import io.swagger.v3.oas.models.OpenAPI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigBeanTest {

    @Test
    void testModelMapperConfig() {
        ModelMapperConfig config = new ModelMapperConfig();
        ModelMapper mapper = config.modelMapper();
        assertThat(mapper).isNotNull();
    }

    @Test
    void testOpenApiConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI api = config.customOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getTitle()).contains("User Service");
    }

    @Test
    void testRedisConfig() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        
        RedisCacheManager manager = config.cacheManager(factory);
        assertThat(manager).isNotNull();
    }
}
