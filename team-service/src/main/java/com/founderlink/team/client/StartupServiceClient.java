package com.founderlink.team.client;

import com.founderlink.team.dto.response.StartupResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "STARTUP-SERVICE",
    fallback = StartupServiceClientFallback.class
)
public interface StartupServiceClient {

    @GetMapping("/startups/{id}")
    StartupResponseDto getStartupById(@PathVariable Long id);
}