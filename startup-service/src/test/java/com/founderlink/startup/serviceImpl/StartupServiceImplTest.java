package com.founderlink.startup.serviceImpl;

import com.founderlink.startup.command.StartupCommandService;
import com.founderlink.startup.dto.request.StartupRequestDto;
import com.founderlink.startup.dto.response.PagedResponse;
import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.StartupStage;
import com.founderlink.startup.query.StartupQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartupServiceImplTest {

    @Mock
    private StartupCommandService commandService;

    @Mock
    private StartupQueryService queryService;

    @InjectMocks
    private StartupServiceImpl startupService;

    @Test
    void createStartup_shouldDelegateToCommandService() {
        StartupRequestDto requestDto = new StartupRequestDto();
        StartupResponseDto responseDto = new StartupResponseDto();
        when(commandService.createStartup(1L, requestDto)).thenReturn(responseDto);

        StartupResponseDto result = startupService.createStartup(1L, requestDto);

        assertThat(result).isEqualTo(responseDto);
        verify(commandService).createStartup(1L, requestDto);
    }

    @Test
    void updateStartup_shouldDelegateToCommandService() {
        StartupRequestDto requestDto = new StartupRequestDto();
        StartupResponseDto responseDto = new StartupResponseDto();
        when(commandService.updateStartup(1L, 2L, requestDto)).thenReturn(responseDto);

        StartupResponseDto result = startupService.updateStartup(1L, 2L, requestDto);

        assertThat(result).isEqualTo(responseDto);
        verify(commandService).updateStartup(1L, 2L, requestDto);
    }

    @Test
    void deleteStartup_shouldDelegateToCommandService() {
        startupService.deleteStartup(1L, 2L);
        verify(commandService).deleteStartup(1L, 2L);
    }

    @Test
    void getStartupById_shouldDelegateToQueryService() {
        StartupResponseDto responseDto = new StartupResponseDto();
        when(queryService.getStartupById(1L)).thenReturn(responseDto);

        StartupResponseDto result = startupService.getStartupById(1L);

        assertThat(result).isEqualTo(responseDto);
        verify(queryService).getStartupById(1L);
    }

    @Test
    void getAllStartups_shouldDelegateToQueryService() {
        PagedResponse<StartupResponseDto> pagedResponse = new PagedResponse<>();
        when(queryService.getAllStartups(any(Pageable.class))).thenReturn(pagedResponse);

        PagedResponse<StartupResponseDto> result = startupService.getAllStartups(0, 10);

        assertThat(result).isEqualTo(pagedResponse);
        verify(queryService).getAllStartups(any(Pageable.class));
    }

    @Test
    void getStartupsByFounderId_shouldDelegateToQueryService() {
        PagedResponse<StartupResponseDto> pagedResponse = new PagedResponse<>();
        when(queryService.getStartupsByFounderId(eq(1L), any(Pageable.class))).thenReturn(pagedResponse);

        PagedResponse<StartupResponseDto> result = startupService.getStartupsByFounderId(1L, 0, 10);

        assertThat(result).isEqualTo(pagedResponse);
        verify(queryService).getStartupsByFounderId(eq(1L), any(Pageable.class));
    }

    @Test
    void searchStartups_shouldDelegateToQueryService() {
        PagedResponse<StartupResponseDto> pagedResponse = new PagedResponse<>();
        BigDecimal min = new BigDecimal("1000");
        BigDecimal max = new BigDecimal("5000");
        when(queryService.searchStartups(eq("Tech"), eq(StartupStage.MVP), eq(min), eq(max), any(Pageable.class)))
                .thenReturn(pagedResponse);

        PagedResponse<StartupResponseDto> result = startupService.searchStartups("Tech", StartupStage.MVP, min, max, 0, 10);

        assertThat(result).isEqualTo(pagedResponse);
        verify(queryService).searchStartups(eq("Tech"), eq(StartupStage.MVP), eq(min), eq(max), any(Pageable.class));
    }
}
