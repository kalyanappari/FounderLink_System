package com.founderlink.User_Service.service;

import com.founderlink.User_Service.command.UserCommandService;
import com.founderlink.User_Service.dto.UserRequestAuthDto;
import com.founderlink.User_Service.dto.UserRequestDto;
import com.founderlink.User_Service.entity.Role;
import com.founderlink.User_Service.query.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceFacadeTest {

    @Mock
    private UserCommandService commandService;

    @Mock
    private UserQueryService queryService;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_shouldDelegateToCommand() {
        UserRequestAuthDto dto = new UserRequestAuthDto();
        userService.createUser(dto);
        verify(commandService).createUser(dto);
    }

    @Test
    void updateUser_shouldDelegateToCommand() {
        UserRequestDto dto = new UserRequestDto();
        userService.updateUser(1L, dto);
        verify(commandService).updateUser(1L, dto);
    }

    @Test
    void getUser_shouldDelegateToQuery() {
        userService.getUser(1L);
        verify(queryService).getUser(1L);
    }

    @Test
    void getAllUsers_shouldDelegateToQuery() {
        Pageable p = Pageable.unpaged();
        userService.getAllUsers("search", p);
        verify(queryService).getAllUsers("search", p);
    }

    @Test
    void getUsersByRole_shouldDelegateToQuery() {
        Pageable p = Pageable.unpaged();
        userService.getUsersByRole(Role.FOUNDER, "search", p);
        verify(queryService).getUsersByRole(Role.FOUNDER, "search", p);
    }

    @Test
    void countByRole_shouldDelegateToQuery() {
        userService.countByRole(Role.INVESTOR);
        verify(queryService).countByRole(Role.INVESTOR);
    }
}
