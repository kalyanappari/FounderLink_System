package com.founderlink.startup.command;

import com.founderlink.startup.dto.request.StartupRequestDto;
import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.Startup;
import com.founderlink.startup.events.StartupCreatedEvent;
import com.founderlink.startup.events.StartupDeletedEvent;
import com.founderlink.startup.events.StartupEventPublisher;
import com.founderlink.startup.exception.ForbiddenAccessException;
import com.founderlink.startup.exception.StartupNotFoundException;
import com.founderlink.startup.mapper.StartupMapper;
import com.founderlink.startup.repository.StartupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartupCommandServiceTest {

    @Mock
    private StartupRepository startupRepository;

    @Mock
    private StartupMapper startupMapper;

    @Mock
    private StartupEventPublisher eventPublisher;

    @InjectMocks
    private StartupCommandService commandService;

    @Test
    void createStartup_shouldSucceed() {
        // Arrange
        Long founderId = 1L;
        StartupRequestDto request = new StartupRequestDto();
        request.setName("Test Startup");
        
        Startup startup = new Startup();
        startup.setId(100L);
        startup.setName("Test Startup");
        startup.setFounderId(founderId);

        when(startupMapper.toEntity(request, founderId)).thenReturn(startup);
        when(startupRepository.save(startup)).thenReturn(startup);
        when(startupMapper.toResponseDto(startup)).thenReturn(new StartupResponseDto());

        // Act
        StartupResponseDto result = commandService.createStartup(founderId, request);

        // Assert
        assertThat(result).isNotNull();
        verify(startupRepository).save(startup);
        verify(eventPublisher).publishStartupCreatedEvent(any(StartupCreatedEvent.class));
    }

    @Test
    void updateStartup_shouldSucceed_whenOwnerMatches() {
        // Arrange
        Long id = 100L;
        Long founderId = 1L;
        StartupRequestDto request = new StartupRequestDto();
        request.setName("Updated Startup");

        Startup existing = new Startup();
        existing.setId(id);
        existing.setFounderId(founderId);

        when(startupRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(startupRepository.save(existing)).thenReturn(existing);
        when(startupMapper.toResponseDto(existing)).thenReturn(new StartupResponseDto());

        // Act
        StartupResponseDto result = commandService.updateStartup(id, founderId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(existing.getName()).isEqualTo("Updated Startup");
        verify(startupRepository).save(existing);
    }

    @Test
    void updateStartup_shouldThrowForbidden_whenOwnerMismatch() {
        // Arrange
        Long id = 100L;
        Long hackerId = 999L;
        Startup existing = new Startup();
        existing.setId(id);
        existing.setFounderId(1L); // Owned by someone else

        when(startupRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> commandService.updateStartup(id, hackerId, new StartupRequestDto()))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void deleteStartup_shouldSoftDelete_whenOwnerMatches() {
        // Arrange
        Long id = 100L;
        Long founderId = 1L;
        Startup existing = new Startup();
        existing.setId(id);
        existing.setFounderId(founderId);
        existing.setIsDeleted(false);

        when(startupRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));

        // Act
        commandService.deleteStartup(id, founderId);

        // Assert
        assertThat(existing.getIsDeleted()).isTrue();
        verify(startupRepository).save(existing);
        verify(eventPublisher).publishStartupDeletedEvent(any(StartupDeletedEvent.class));
    }

    @Test
    void deleteStartup_shouldThrowNotFound_whenMissing() {
        // Arrange
        when(startupRepository.findByIdAndIsDeletedFalse(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> commandService.deleteStartup(1L, 1L))
                .isInstanceOf(StartupNotFoundException.class);
    }
}
