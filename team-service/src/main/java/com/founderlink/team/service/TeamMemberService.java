package com.founderlink.team.service;

import java.util.List;

import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;

public interface TeamMemberService {

    // Co-founder joins team
    TeamMemberResponseDto joinTeam(
            Long userId,
            JoinTeamRequestDto requestDto);

    // Get all members of a startup
    List<TeamMemberResponseDto> getTeamByStartupId(
            Long startupId,Long userId,String userRole);

    // Founder removes a team member
    void removeTeamMember(
            Long teamMemberId,
            Long founderId);
    
    boolean isTeamMember(Long startupId, Long userId);
}