package com.founderlink.team.query;

import com.founderlink.team.client.StartupServiceClient;
import com.founderlink.team.dto.response.StartupResponseDto;
import com.founderlink.team.dto.response.TeamMemberResponseDto;
import com.founderlink.team.entity.TeamMember;
import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.exception.StartupNotFoundException;
import com.founderlink.team.exception.StartupServiceUnavailableException;
import com.founderlink.team.mapper.TeamMemberMapper;
import com.founderlink.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberQueryServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamMemberMapper teamMemberMapper;

    @Mock
    private StartupServiceClient startupServiceClient;

    @InjectMocks
    private TeamMemberQueryService queryService;

    private TeamMember teamMember;
    private TeamMemberResponseDto responseDto;
    private StartupResponseDto startupResponse;

    @BeforeEach
    void setUp() {
        teamMember = new TeamMember();
        teamMember.setId(1L);
        teamMember.setStartupId(100L);
        teamMember.setUserId(200L);

        responseDto = new TeamMemberResponseDto();
        responseDto.setId(1L);

        startupResponse = new StartupResponseDto();
        startupResponse.setId(100L);
        startupResponse.setFounderId(5L);
    }

    @Test
    void getTeamByStartupId_FounderRole_Success() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        when(teamMemberRepository.findByStartupIdAndIsActiveTrue(100L)).thenReturn(List.of(teamMember));
        when(teamMemberMapper.toResponseDto(any(TeamMember.class))).thenReturn(responseDto);

        List<TeamMemberResponseDto> result = queryService.getTeamByStartupId(100L, 5L, "ROLE_FOUNDER");

        assertThat(result).hasSize(1);
        verify(startupServiceClient).getStartupById(100L);
    }

    @Test
    void getTeamByStartupId_OtherRole_Success() {
        when(teamMemberRepository.findByStartupIdAndIsActiveTrue(100L)).thenReturn(List.of(teamMember));
        when(teamMemberMapper.toResponseDto(any(TeamMember.class))).thenReturn(responseDto);

        List<TeamMemberResponseDto> result = queryService.getTeamByStartupId(100L, 5L, "ROLE_INVESTOR");

        assertThat(result).hasSize(1);
        verifyNoInteractions(startupServiceClient);
    }

    @Test
    void getMemberHistory_Success() {
        when(teamMemberRepository.findByUserId(200L)).thenReturn(List.of(teamMember));
        when(teamMemberMapper.toResponseDto(any(TeamMember.class))).thenReturn(responseDto);

        List<TeamMemberResponseDto> result = queryService.getMemberHistory(200L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getActiveMemberRoles_Success() {
        when(teamMemberRepository.findByUserIdAndIsActiveTrue(200L)).thenReturn(List.of(teamMember));
        when(teamMemberMapper.toResponseDto(any(TeamMember.class))).thenReturn(responseDto);

        List<TeamMemberResponseDto> result = queryService.getActiveMemberRoles(200L);

        assertThat(result).hasSize(1);
    }

    @Test
    void isTeamMember_Success() {
        when(teamMemberRepository.existsByStartupIdAndUserIdAndIsActiveTrue(100L, 200L)).thenReturn(true);

        boolean result = queryService.isTeamMember(100L, 200L);

        assertThat(result).isTrue();
    }

    @Test
    void getTeamByStartupIdFallback_ShouldThrowProperException() {
        RuntimeException snf = new StartupNotFoundException("Not found");
        assertThatThrownBy(() -> queryService.getTeamByStartupIdFallback(100L, 5L, "ROLE_FOUNDER", snf))
                .isEqualTo(snf);

        RuntimeException fae = new ForbiddenAccessException("Forbidden");
        assertThatThrownBy(() -> queryService.getTeamByStartupIdFallback(100L, 5L, "ROLE_FOUNDER", fae))
                .isEqualTo(fae);

        RuntimeException generic = new RuntimeException("Generic error");
        assertThatThrownBy(() -> queryService.getTeamByStartupIdFallback(100L, 5L, "ROLE_FOUNDER", generic))
                .isInstanceOf(StartupServiceUnavailableException.class)
                .hasMessageContaining("Startup service is temporarily unavailable");
    }
    
    @Test
    void verifyFounderOwnsStartup_StartupNotFound() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);
        
        assertThatThrownBy(() -> queryService.getTeamByStartupId(100L, 5L, "ROLE_FOUNDER"))
                .isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void verifyFounderOwnsStartup_Forbidden() {
        startupResponse.setFounderId(99L);
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        
        assertThatThrownBy(() -> queryService.getTeamByStartupId(100L, 5L, "ROLE_FOUNDER"))
                .isInstanceOf(ForbiddenAccessException.class);
    }
}
