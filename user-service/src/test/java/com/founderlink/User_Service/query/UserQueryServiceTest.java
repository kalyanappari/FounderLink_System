package com.founderlink.User_Service.query;

import com.founderlink.User_Service.dto.UserResponseDto;
import com.founderlink.User_Service.entity.Role;
import com.founderlink.User_Service.entity.User;
import com.founderlink.User_Service.exceptions.UserNotFoundException;
import com.founderlink.User_Service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserQueryService queryService;

    @Test
    void getUser_shouldReturnDto_whenUserExists() {
        // Arrange
        User user = new User();
        user.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(modelMapper.map(user, UserResponseDto.class)).thenReturn(new UserResponseDto());

        // Act
        UserResponseDto result = queryService.getUser(1L);

        // Assert
        assertThat(result).isNotNull();
        verify(repository).findById(1L);
    }

    @Test
    void getUser_shouldThrowNotFound_whenUserMissing() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queryService.getUser(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getAllUsers_shouldReturnPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(new User()));
        when(repository.searchUsers(null, "test", pageable)).thenReturn(page);
        when(modelMapper.map(any(), eq(UserResponseDto.class))).thenReturn(new UserResponseDto());

        // Act
        var result = queryService.getAllUsers("test", pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getUsersByRole_shouldFilterByRole() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(new User()));
        when(repository.searchUsers(Role.FOUNDER, null, pageable)).thenReturn(page);

        // Act
        var result = queryService.getUsersByRole(Role.FOUNDER, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        verify(repository).searchUsers(Role.FOUNDER, null, pageable);
    }

    @Test
    void countByRole_shouldCallRepository() {
        // Arrange
        when(repository.countByRole(Role.INVESTOR)).thenReturn(5L);

        // Act
        long count = queryService.countByRole(Role.INVESTOR);

        // Assert
        assertThat(count).isEqualTo(5);
        verify(repository).countByRole(Role.INVESTOR);
    }
}
