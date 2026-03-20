package com.founderlink.team.client;

import com.founderlink.team.dto.response.StartupResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupServiceClientFallback
        implements StartupServiceClient {

    @Override
    public StartupResponseDto getStartupById(Long id) {
        log.error("Startup Service is down. " +
                "Cannot fetch startup with id: {}", id);
        return null;
    }
}
