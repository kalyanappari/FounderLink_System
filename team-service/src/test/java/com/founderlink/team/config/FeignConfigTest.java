package com.founderlink.team.config;

import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.exception.StartupNotFoundException;
import com.founderlink.team.exception.StartupServiceServerException;
import com.founderlink.team.exception.StartupServiceUnavailableException;
import feign.Request;
import feign.Response;
import feign.Retryer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeignConfigTest {

    private final FeignConfig feignConfig = new FeignConfig();
    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void feignConfig_Beans_ShouldInitialize() {
        assertThat(feignConfig.errorDecoder()).isNotNull();
        assertThat(feignConfig.feignRetryer()).isEqualTo(Retryer.NEVER_RETRY);
        assertThat(feignConfig.feignLoggerLevel()).isNotNull();
        assertThat(feignConfig.requestOptions()).isNotNull();
    }

    @Test
    void decode_403_ShouldReturnForbiddenAccessException() {
        Response response = createResponse(403, "Forbidden");
        Exception ex = decoder.decode("method", response);
        assertThat(ex).isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void decode_404_ShouldReturnStartupNotFoundException() {
        Response response = createResponse(404, "Not Found");
        Exception ex = decoder.decode("method", response);
        assertThat(ex).isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void decode_503_ShouldReturnStartupServiceUnavailableException() {
        Response response = createResponse(503, "Unavailable");
        Exception ex = decoder.decode("method", response);
        assertThat(ex).isInstanceOf(StartupServiceUnavailableException.class);
    }

    @Test
    void decode_500_ShouldReturnStartupServiceServerException() {
        Response response = createResponse(500, "Server Error");
        Exception ex = decoder.decode("method", response);
        assertThat(ex).isInstanceOf(StartupServiceServerException.class);
    }

    @Test
    void decode_400_ShouldReturnRuntimeException() {
        Response response = createResponse(400, "Bad Request");
        Exception ex = decoder.decode("method", response);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("Feign error [400]");
    }

    @Test
    void decode_NoReason_ShouldUseDefault() {
        Response response = Response.builder()
                .status(500)
                .reason(null)
                .request(mock(Request.class))
                .headers(new HashMap<>())
                .build();
        Exception ex = decoder.decode("method", response);
        assertThat(ex.getMessage()).contains("No reason provided");
    }

    private Response createResponse(int status, String reason) {
        return Response.builder()
                .status(status)
                .reason(reason)
                .request(mock(Request.class))
                .headers(new HashMap<>())
                .build();
    }
}
