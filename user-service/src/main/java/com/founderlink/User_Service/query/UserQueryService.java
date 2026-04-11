package com.founderlink.User_Service.query;

import java.util.List;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.founderlink.User_Service.dto.UserResponseDto;
import com.founderlink.User_Service.entity.Role;
import com.founderlink.User_Service.exceptions.UserNotFoundException;
import com.founderlink.User_Service.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository repository;
    private final ModelMapper modelMapper;

    /**
     * QUERY: Get single user by ID.
     * Cache key = userId. Cache hit → skip DB.
     */
    @Cacheable(value = "userById", key = "#id")
    public UserResponseDto getUser(Long id) {
        log.info("QUERY - getUser: id={} (cache miss, hitting DB)", id);
        return repository.findById(id)
                .map(u -> modelMapper.map(u, UserResponseDto.class))
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    /**
     * QUERY: Get all users with pagination and optional search.
     */
    @Cacheable(value = "usersPaged", key = "'all-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + (#search != null ? #search : '')")
    public com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> getAllUsers(String search, org.springframework.data.domain.Pageable pageable) {
        log.info("QUERY - getAllUsers paged (cache miss, hitting DB)");
        return searchUsers(null, search, pageable);
    }

    /**
     * QUERY: Get users filtered by role with pagination and optional search.
     */
    @Cacheable(value = "usersByRolePaged", key = "#role.name() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + (#search != null ? #search : '')")
    public com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> getUsersByRole(Role role, String search, org.springframework.data.domain.Pageable pageable) {
        log.info("QUERY - getUsersByRole paged: role={} (cache miss, hitting DB)", role);
        return searchUsers(role, search, pageable);
    }

    private com.founderlink.User_Service.dto.PagedResponse<UserResponseDto> searchUsers(Role role, String search, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<com.founderlink.User_Service.entity.User> page = repository.searchUsers(role, search, pageable);
        return com.founderlink.User_Service.dto.PagedResponse.of(page.map(u -> modelMapper.map(u, UserResponseDto.class)));
    }

    /**
     * QUERY: Count users by role.
     */
    @Cacheable(value = "userCountByRole", key = "#role.name()")
    public long countByRole(Role role) {
        log.info("QUERY - countByRole: role={} (cache miss, hitting DB)", role);
        return repository.countByRole(role);
    }
}
