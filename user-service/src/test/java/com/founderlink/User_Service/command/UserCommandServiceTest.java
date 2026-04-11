package com.founderlink.User_Service.command;

import com.founderlink.User_Service.dto.UserRequestAuthDto;
import com.founderlink.User_Service.dto.UserRequestDto;
import com.founderlink.User_Service.dto.UserResponseDto;
import com.founderlink.User_Service.entity.Role;
import com.founderlink.User_Service.entity.User;
import com.founderlink.User_Service.exceptions.ConflictException;
import com.founderlink.User_Service.exceptions.UserNotFoundException;
import com.founderlink.User_Service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserCommandService commandService;

    @Test
    void createUser_shouldCreateNewUser_whenNotExists() {
        // Arrange
        UserRequestAuthDto dto = new UserRequestAuthDto();
        dto.setUserId(1L);
        dto.setEmail("test@founderlink.com");
        dto.setRole(Role.FOUNDER);

        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);
        when(modelMapper.map(any(), eq(UserResponseDto.class))).thenReturn(new UserResponseDto());

        // Act
        UserResponseDto result = commandService.createUser(dto);

        // Assert
        assertThat(result).isNotNull();
        verify(repository).save(any(User.class));
    }

    @Test
    void createUser_shouldReturnExistingUser_whenSameDataExists() {
        // Arrange
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("test@founderlink.com");
        existing.setRole(Role.FOUNDER);

        UserRequestAuthDto dto = new UserRequestAuthDto();
        dto.setUserId(1L);
        dto.setEmail("test@founderlink.com");
        dto.setRole(Role.FOUNDER);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(modelMapper.map(existing, UserResponseDto.class)).thenReturn(new UserResponseDto());

        // Act
        UserResponseDto result = commandService.createUser(dto);

        // Assert
        assertThat(result).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    void createUser_shouldThrowConflict_whenEmailMismatch() {
        // Arrange
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("old@founderlink.com");
        existing.setRole(Role.FOUNDER);

        UserRequestAuthDto dto = new UserRequestAuthDto();
        dto.setUserId(1L);
        dto.setEmail("new@founderlink.com");
        dto.setRole(Role.FOUNDER);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> commandService.createUser(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("User identity data does not match");
    }

    @Test
    void createUser_shouldThrowConflict_whenRoleMismatch() {
        // Arrange
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("test@founderlink.com");
        existing.setRole(Role.FOUNDER);

        UserRequestAuthDto dto = new UserRequestAuthDto();
        dto.setUserId(1L);
        dto.setEmail("test@founderlink.com");
        dto.setRole(Role.INVESTOR);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> commandService.createUser(dto))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUser_shouldApplyAllFields_whenProvided() {
        // Arrange
        User existing = new User();
        existing.setId(1L);

        UserRequestDto dto = new UserRequestDto();
        dto.setName("New Name");
        dto.setSkills("Java, Spring");
        dto.setExperience("5 years");
        dto.setBio("Detailed bio");
        dto.setPortfolioLinks("link1, link2");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(modelMapper.map(existing, UserResponseDto.class)).thenReturn(new UserResponseDto());

        // Act
        commandService.updateUser(1L, dto);

        // Assert
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getSkills()).isEqualTo("Java, Spring");
        assertThat(existing.getExperience()).isEqualTo("5 years");
        assertThat(existing.getBio()).isEqualTo("Detailed bio");
        assertThat(existing.getPortfolioLinks()).isEqualTo("link1, link2");
        verify(repository).save(existing);
    }

    @Test
    void updateUser_shouldKeepExistingFields_whenDtoFieldsAreNull() {
        // Arrange
        User existing = new User();
        existing.setId(1L);
        existing.setName("Original Name");
        existing.setSkills("Original Skills");

        UserRequestDto dto = new UserRequestDto(); // All fields null

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(modelMapper.map(existing, UserResponseDto.class)).thenReturn(new UserResponseDto());

        // Act
        commandService.updateUser(1L, dto);

        // Assert
        assertThat(existing.getName()).isEqualTo("Original Name");
        assertThat(existing.getSkills()).isEqualTo("Original Skills");
        verify(repository).save(existing);
    }

    @Test
    void updateUser_shouldThrowNotFound_whenUserMissing() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> commandService.updateUser(1L, new UserRequestDto()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
