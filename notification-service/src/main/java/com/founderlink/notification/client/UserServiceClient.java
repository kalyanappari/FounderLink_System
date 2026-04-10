package com.founderlink.notification.client;

import com.founderlink.notification.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/users")
    com.founderlink.notification.dto.PagedResponse<UserDTO> getAllUsers(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "10") int size);

    @GetMapping("/users/role/{role}")
    com.founderlink.notification.dto.PagedResponse<UserDTO> getUsersByRole(
            @PathVariable("role") String role,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "10") int size);

    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable Long id);

}
