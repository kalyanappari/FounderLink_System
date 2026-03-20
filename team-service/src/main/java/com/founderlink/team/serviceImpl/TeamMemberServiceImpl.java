package com.founderlink.team.serviceImpl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.founderlink.team.client.StartupServiceClient;
import com.founderlink.team.dto.request.JoinTeamRequestDto;
import com.founderlink.team.dto.response.StartupResponseDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.entity.Invitation;
import com.founderlink.team.entity.InvitationStatus;
import com.founderlink.team.entity.TeamMember;
import com.founderlink.team.exception.AlreadyTeamMemberException;
import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.exception.InvalidInvitationStatusException;
import com.founderlink.team.exception.InvitationNotFoundException;
import com.founderlink.team.exception.StartupNotFoundException;
import com.founderlink.team.exception.TeamMemberNotFoundException;
import com.founderlink.team.exception.UnauthorizedAccessException;
import com.founderlink.team.mapper.TeamMemberMapper;
import com.founderlink.team.repository.InvitationRepository;
import com.founderlink.team.repository.TeamMemberRepository;
import com.founderlink.team.service.TeamMemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamMemberServiceImpl implements TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final InvitationRepository invitationRepository;
    private final TeamMemberMapper teamMemberMapper;
    private final StartupServiceClient startupServiceClient;

    // JOIN TEAM
    
    @Override
    @Transactional
    public TeamMemberResponseDto joinTeam(Long userId,
                                           JoinTeamRequestDto requestDto) {

        // Find invitation directly by ID
        Invitation invitation = invitationRepository
                .findById(requestDto.getInvitationId())
                .orElseThrow(() -> new InvitationNotFoundException(
                        "Invitation not found with id: "
                        + requestDto.getInvitationId()));

        // Verify this invitation belongs to this user
        if (!invitation.getInvitedUserId().equals(userId)) {
            throw new UnauthorizedAccessException(
                    "This invitation does not belong to you");
        }

        // Verify invitation is PENDING
        if (!invitation.getStatus().equals(InvitationStatus.PENDING)) {
            throw new InvalidInvitationStatusException(
                    "Only PENDING invitations can be accepted");
        }

        // Edge case — check already a member
        if (teamMemberRepository.existsByStartupIdAndUserId(
                invitation.getStartupId(), userId)) {
            throw new AlreadyTeamMemberException(
                    "You are already a member of this startup");
        }

        // Edge case — check role already taken
        if (teamMemberRepository.existsByStartupIdAndRole(
                invitation.getStartupId(), invitation.getRole())) {
            throw new AlreadyTeamMemberException(
                    "This role is already filled in the team");
        }

        // Create team member
        TeamMember teamMember = new TeamMember();
        teamMember.setStartupId(invitation.getStartupId());
        teamMember.setUserId(userId);
        teamMember.setRole(invitation.getRole());

        // Save team member
        TeamMember savedMember = teamMemberRepository
                .save(teamMember);

        // Update invitation to ACCEPTED
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("UserId: {} joined startupId: {} as {}",
                userId,
                invitation.getStartupId(),
                invitation.getRole());

        return teamMemberMapper.toResponseDto(savedMember);
    }

    // GET TEAM BY STARTUP ID
    
    @Override
    public List<TeamMemberResponseDto> getTeamByStartupId(Long startupId,Long userId,String userRole) {
    	
    	   if (userRole.equals("ROLE_FOUNDER")) {
               verifyFounderOwnsStartup(startupId, userId);
           }

        return teamMemberRepository
                .findByStartupId(startupId)
                .stream()
                .map(teamMemberMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    // REMOVE TEAM MEMBER
    
    @Override
    @Transactional
    public void removeTeamMember(Long teamMemberId, Long founderId) {

        // Find team member
        TeamMember teamMember = teamMemberRepository
                .findById(teamMemberId)
                .orElseThrow(() -> new TeamMemberNotFoundException(
                        "Team member not found with id: " + teamMemberId));
        
        verifyFounderOwnsStartup(teamMember.getStartupId(),founderId);

        // Edge case — founder cannot remove themselves
        if (teamMember.getUserId().equals(founderId)) {
            throw new UnauthorizedAccessException(
                    "Founder cannot remove themselves from the team");
        }

        // Remove team member
        teamMemberRepository.delete(teamMember);

        log.info("TeamMember removed with id: {} by founderId: {}",
                teamMemberId, founderId);
    }
    
    @Override
    public boolean isTeamMember(Long startupId, Long userId) {
        return teamMemberRepository
                .existsByStartupIdAndUserId(startupId, userId);
    }
    
    public void verifyFounderOwnsStartup(
            Long startupId,
            Long founderId) {

        // Call Startup Service
        StartupResponseDto startup = startupServiceClient
                .getStartupById(startupId);

        // Startup not found
        if (startup == null) {
            throw new StartupNotFoundException(
                    "Startup not found with id: " + startupId);
        }

        // Founder does not own startup
        if (!startup.getFounderId().equals(founderId)) {
            throw new ForbiddenAccessException(
                    "You are not authorized to " +
                    "perform this action on this startup");
        }
    }
}