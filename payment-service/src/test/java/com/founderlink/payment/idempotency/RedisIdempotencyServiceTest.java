package com.founderlink.payment.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisIdempotencyService redisIdempotencyService;

    private final String key = "test-key";
    private final String redisKey = "idempotency:test-key";
    private final Long paymentId = 123L;

    @BeforeEach
    void setUp() {
        // opsForValue() usually returns valueOperations
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("storeIdempotencyKey - success")
    void storeIdempotencyKey_Success() {
        // Act
        redisIdempotencyService.storeIdempotencyKey(key, paymentId, 3600);

        // Assert
        verify(valueOperations, times(1)).set(redisKey, paymentId, 3600, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("getPaymentIdByIdempotencyKey - found")
    void getPaymentIdByIdempotencyKey_Found() {
        // Arrange
        when(valueOperations.get(redisKey)).thenReturn(paymentId);

        // Act
        Optional<Long> result = redisIdempotencyService.getPaymentIdByIdempotencyKey(key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(paymentId, result.get());
    }

    @Test
    @DisplayName("getPaymentIdByIdempotencyKey - not found")
    void getPaymentIdByIdempotencyKey_NotFound() {
        // Arrange
        when(valueOperations.get(redisKey)).thenReturn(null);

        // Act
        Optional<Long> result = redisIdempotencyService.getPaymentIdByIdempotencyKey(key);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("idempotencyKeyExists - true")
    void idempotencyKeyExists_True() {
        // Arrange
        when(redisTemplate.hasKey(redisKey)).thenReturn(true);

        // Act
        boolean result = redisIdempotencyService.idempotencyKeyExists(key);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("idempotencyKeyExists - false")
    void idempotencyKeyExists_False() {
        // Arrange
        when(redisTemplate.hasKey(redisKey)).thenReturn(false);

        // Act
        boolean result = redisIdempotencyService.idempotencyKeyExists(key);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("idempotencyKeyExists - null from Redis")
    void idempotencyKeyExists_Null() {
        // Arrange
        when(redisTemplate.hasKey(redisKey)).thenReturn(null);

        // Act
        boolean result = redisIdempotencyService.idempotencyKeyExists(key);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("removeIdempotencyKey - success")
    void removeIdempotencyKey_Success() {
        // Arrange
        when(redisTemplate.delete(redisKey)).thenReturn(true);

        // Act
        redisIdempotencyService.removeIdempotencyKey(key);

        // Assert
        verify(redisTemplate, times(1)).delete(redisKey);
    }

    @Test
    @DisplayName("removeIdempotencyKey - null from Redis")
    void removeIdempotencyKey_Null() {
        // Arrange
        when(redisTemplate.delete(redisKey)).thenReturn(null);

        // Act
        redisIdempotencyService.removeIdempotencyKey(key);

        // Assert
        verify(redisTemplate, times(1)).delete(redisKey);
    }

    @Test
    @DisplayName("removeIdempotencyKey - false from Redis")
    void removeIdempotencyKey_False() {
        // Arrange
        when(redisTemplate.delete(redisKey)).thenReturn(false);

        // Act
        redisIdempotencyService.removeIdempotencyKey(key);

        // Assert
        verify(redisTemplate, times(1)).delete(redisKey);
    }

    @Test
    @DisplayName("getTimeToLive - returns ttl")
    void getTimeToLive_Success() {
        // Arrange
        when(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).thenReturn(300L);

        // Act
        long ttl = redisIdempotencyService.getTimeToLive(key);

        // Assert
        assertEquals(300, ttl);
    }

    @Test
    @DisplayName("getTimeToLive - null from Redis")
    void getTimeToLive_Null() {
        // Arrange
        when(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).thenReturn(null);

        // Act
        long ttl = redisIdempotencyService.getTimeToLive(key);

        // Assert
        assertEquals(-1, ttl);
    }
}
