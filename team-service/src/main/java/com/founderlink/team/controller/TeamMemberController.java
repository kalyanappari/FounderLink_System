package com.founderlink.team.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.ApiResponse;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.service.TeamMemberService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    // JOIN TEAM
    // POST /teams/join
    // Called by → CO-FOUNDER

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<?>> joinTeam(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody JoinTeamRequestDto requestDto) {

        if (!userRole.equals("ROLE_COFOUNDER")) {
            throw new ForbiddenAccessException(
                    "Access denied. Only CO-FOUNDERS can join a team");
        }

        TeamMemberResponseDto response = teamMemberService
                .joinTeam(userId, requestDto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(
                        "Successfully joined the team",
                        response));
    }

    // GET TEAM MEMBERS BY STARTUP ID
    // GET /teams/startup/{startupId}
    // Called by → ALL ROLES
 
    @GetMapping("/startup/{startupId}")
    public ResponseEntity<ApiResponse<?>> getTeamByStartupId(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long startupId) {

        // CoFounder membership check
        if (userRole.equals("ROLE_COFUNDER")) {
            boolean isMember = teamMemberService
                    .isTeamMember(startupId, userId);
            if (!isMember) {
                throw new ForbiddenAccessException(
                        "Access denied. You are not a member of this startup");
            }
        }

        // Unknown role check
        if (!userRole.equals("ROLE_FOUNDER") &&
            !userRole.equals("ROLE_COFUNDER") &&
            !userRole.equals("ROLE_INVESTOR") &&
            !userRole.equals("ROLE_ADMIN")) {
            throw new ForbiddenAccessException(
                    "Access denied");
        }

        List<TeamMemberResponseDto> response = teamMemberService
                .getTeamByStartupId(startupId,userId,userRole);

        return ResponseEntity
                .ok(new ApiResponse<>(
                        "Team members fetched successfully",
                        response));
    }

    // REMOVE TEAM MEMBER
    // DELETE /teams/{teamMemberId}
    // Called by → FOUNDER
    
    @DeleteMapping("/{teamMemberId}")
    public ResponseEntity<ApiResponse<?>> removeTeamMember(
            @RequestHeader("X-User-Id") Long founderId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long teamMemberId) {

        if (!userRole.equals("ROLE_FOUNDER")) {
            throw new ForbiddenAccessException(
                    "Access denied. Only FOUNDERS can remove team members");
        }

        // TODO: FeignClient verify founder owns startup

        teamMemberService.removeTeamMember(
                teamMemberId, founderId);

        return ResponseEntity
                .ok(new ApiResponse<>(
                        "Team member removed successfully",
                        null));
    }
}