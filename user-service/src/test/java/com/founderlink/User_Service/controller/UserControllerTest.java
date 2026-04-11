package com.founderlink.User_Service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.User_Service.dto.UserRequestAuthDto;
import com.founderlink.User_Service.dto.UserRequestDto;
import com.founderlink.User_Service.dto.UserResponseDto;
import com.founderlink.User_Service.entity.Role;
import com.founderlink.User_Service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = UserController.class,
    properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
    }
)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for unit test
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserResponseDto responseDto;

    @BeforeEach
    void setUp() {
        responseDto = new UserResponseDto();
        responseDto.setId(1L);
        responseDto.setEmail("test@founderlink.com");
        responseDto.setRole(Role.FOUNDER);
    }

    @Test
    void createUser_shouldReturnOk() throws Exception {
        UserRequestAuthDto requestDto = new UserRequestAuthDto();
        requestDto.setUserId(1L);
        requestDto.setEmail("test@founderlink.com");

        when(userService.createUser(any(UserRequestAuthDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/users/internal")
                .header("X-Auth-Source", "gateway")
                .header("X-Internal-Secret", "my-founderlink-internal-secret-2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@founderlink.com"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void getUser_shouldReturnUser() throws Exception {
        when(userService.getUser(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        UserRequestDto requestDto = new UserRequestDto();
        requestDto.setName("Updated Name");

        when(userService.updateUser(eq(1L), any(UserRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(put("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    void getAllUsers_shouldReturnPagedResponse() throws Exception {
        com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> pagedResponse = new com.founderlink.User_Service.dto.PagedResponse<>();
        pagedResponse.setContent(Collections.singletonList(responseDto));

        when(userService.getAllUsers(any(), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/users")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(1));
    }

    @Test
    void getUsersByRole_shouldReturnPagedResponse() throws Exception {
        com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> pagedResponse = new com.founderlink.User_Service.dto.PagedResponse<>();
        pagedResponse.setContent(Collections.singletonList(responseDto));

        when(userService.getUsersByRole(eq(Role.FOUNDER), any(), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/users/role/FOUNDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(1));
    }

    @Test
    void getUsersByRole_shouldReturnForbidden_forAdminRole() throws Exception {
        mockMvc.perform(get("/users/role/ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsersByRole_shouldReturnBadRequest_forInvalidRole() throws Exception {
        mockMvc.perform(get("/users/role/INVALID_ROLE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_shouldReturnForbidden_whenHeadersMissing() throws Exception {
        UserRequestAuthDto requestDto = new UserRequestAuthDto();
        requestDto.setUserId(1L);
        requestDto.setEmail("test@founderlink.com");

        mockMvc.perform(post("/users/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_shouldReturnForbidden_whenSecretWrong() throws Exception {
        UserRequestAuthDto requestDto = new UserRequestAuthDto();
        requestDto.setUserId(1L);
        requestDto.setEmail("test@founderlink.com");

        mockMvc.perform(post("/users/internal")
                .header("X-Auth-Source", "gateway")
                .header("X-Internal-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_withSearch_shouldReturnPagedResponse() throws Exception {
        com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> pagedResponse = new com.founderlink.User_Service.dto.PagedResponse<>();
        pagedResponse.setContent(Collections.singletonList(responseDto));

        when(userService.getAllUsers(eq("testSearch"), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/users")
                .param("search", "testSearch"))
                .andExpect(status().isOk());
    }

    @Test
    void getUsersByRole_withSearch_shouldReturnPagedResponse() throws Exception {
        com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> pagedResponse = new com.founderlink.User_Service.dto.PagedResponse<>();
        pagedResponse.setContent(Collections.singletonList(responseDto));

        when(userService.getUsersByRole(eq(Role.FOUNDER), eq("searchQuery"), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/users/role/FOUNDER")
                .param("search", "searchQuery"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublicStats_shouldReturnStats() throws Exception {
        mockMvc.perform(get("/users/public/stats"))
                .andExpect(status().isOk());
    }
}
