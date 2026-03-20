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
import com.founderlink.team.dto.response.TeamMemberResponseDto;
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
    public ResponseEntity<TeamMemberResponseDto> joinTeam(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody JoinTeamRequestDto requestDto) {

        // Only co-founder can join
        if (!userRole.equals("ROLE_COFOUNDER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        TeamMemberResponseDto response = teamMemberService
                .joinTeam(userId, requestDto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET TEAM MEMBERS BY STARTUP ID
    // GET /teams/startup/{startupId}
    // Called by → ALL ROLES
 
    @GetMapping("/startup/{startupId}")
    public ResponseEntity<List<TeamMemberResponseDto>> getTeamByStartupId(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long startupId) {

        if (userRole.equals("ROLE_FOUNDER")) {
        	
        	 // We need to verify founder owns this startup
            // For now check via FeignClient later
            // Currently trust X-User-Id with startupId match
            // TODO: Add FeignClient verification later

            List<TeamMemberResponseDto> response = teamMemberService
                    .getTeamByStartupId(startupId);
            return ResponseEntity.ok(response);
        }

        // ROLE_COFUNDER
        // only if they are member of this startup
        if (userRole.equals("ROLE_COFUNDER")) {
            boolean isMember = teamMemberService
                    .isTeamMember(startupId, userId);

            if (!isMember) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .build();
            }

            List<TeamMemberResponseDto> response = teamMemberService
                    .getTeamByStartupId(startupId);
            return ResponseEntity.ok(response);
        }

        // ROLE_INVESTOR → allow all
        if (userRole.equals("ROLE_INVESTOR")) {
            List<TeamMemberResponseDto> response = teamMemberService
                    .getTeamByStartupId(startupId);
            return ResponseEntity.ok(response);
        }

        // ROLE_ADMIN → allow all
        if (userRole.equals("ROLE_ADMIN")) {
            List<TeamMemberResponseDto> response = teamMemberService
                    .getTeamByStartupId(startupId);
            return ResponseEntity.ok(response);
        }

        // Unknown role
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .build();
    }

    // REMOVE TEAM MEMBER
    // DELETE /teams/{teamMemberId}
    // Called by → FOUNDER
    
    @DeleteMapping("/{teamMemberId}")
    public ResponseEntity<String> removeTeamMember(
            @RequestHeader("X-User-Id") Long founderId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long teamMemberId) {

        // Only founder can remove members
        if (!userRole.equals("ROLE_FOUNDER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        teamMemberService.removeTeamMember(teamMemberId, founderId);

        return ResponseEntity.ok("Team member removed successfully");
    }
}