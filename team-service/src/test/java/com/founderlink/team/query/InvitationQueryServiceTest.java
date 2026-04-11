package com.founderlink.team.query;

import com.founderlink.team.client.StartupServiceClient;
import com.founderlink.team.dto.response.InvitationResponseDto;
import com.founderlink.team.dto.response.StartupResponseDto;
import com.founderlink.team.entity.Invitation;
import com.founderlink.team.exception.ForbiddenAccessException;
import com.founderlink.team.exception.StartupNotFoundException;
import com.founderlink.team.exception.StartupServiceUnavailableException;
import com.founderlink.team.mapper.InvitationMapper;
import com.founderlink.team.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationQueryServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private InvitationMapper invitationMapper;

    @Mock
    private StartupServiceClient startupServiceClient;

    @InjectMocks
    private InvitationQueryService queryService;

    private Invitation invitation;
    private InvitationResponseDto responseDto;
    private StartupResponseDto startupResponse;

    @BeforeEach
    void setUp() {
        invitation = new Invitation();
        invitation.setId(1L);
        invitation.setStartupId(100L);
        invitation.setInvitedUserId(200L);
        invitation.setFounderId(5L);

        responseDto = new InvitationResponseDto();
        responseDto.setId(1L);

        startupResponse = new StartupResponseDto();
        startupResponse.setId(100L);
        startupResponse.setFounderId(5L);
    }

    @Test
    void getInvitationsByUserId_Success() {
        when(invitationRepository.findByInvitedUserId(200L)).thenReturn(List.of(invitation));
        when(invitationMapper.toResponseDto(any(Invitation.class))).thenReturn(responseDto);

        List<InvitationResponseDto> results = queryService.getInvitationsByUserId(200L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getInvitationsByStartupId_Success() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);
        when(invitationRepository.findByStartupId(100L)).thenReturn(List.of(invitation));
        when(invitationMapper.toResponseDto(any(Invitation.class))).thenReturn(responseDto);

        List<InvitationResponseDto> results = queryService.getInvitationsByStartupId(100L, 5L);

        assertThat(results).hasSize(1);
        verify(startupServiceClient).getStartupById(100L);
    }

    @Test
    void getInvitationsByStartupId_StartupNotFound() {
        when(startupServiceClient.getStartupById(100L)).thenReturn(null);

        assertThatThrownBy(() -> queryService.getInvitationsByStartupId(100L, 5L))
                .isInstanceOf(StartupNotFoundException.class)
                .hasMessageContaining("Startup not found");
    }

    @Test
    void getInvitationsByStartupId_Forbidden() {
        startupResponse.setFounderId(99L); // Wrong founder
        when(startupServiceClient.getStartupById(100L)).thenReturn(startupResponse);

        assertThatThrownBy(() -> queryService.getInvitationsByStartupId(100L, 5L))
                .isInstanceOf(ForbiddenAccessException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void getInvitationsByStartupIdFallback_ShouldThrowProperException() {
        // Test various throwables in fallback
        
        // Case 1: StartupNotFoundException
        RuntimeException snf = new StartupNotFoundException("Not found");
        assertThatThrownBy(() -> queryService.getInvitationsByStartupIdFallback(100L, 5L, snf))
                .isEqualTo(snf);

        // Case 2: ForbiddenAccessException
        RuntimeException fae = new ForbiddenAccessException("Forbidden");
        assertThatThrownBy(() -> queryService.getInvitationsByStartupIdFallback(100L, 5L, fae))
                .isEqualTo(fae);

        // Case 3: Generic Exception -> StartupServiceUnavailableException
        RuntimeException generic = new RuntimeException("Generic error");
        assertThatThrownBy(() -> queryService.getInvitationsByStartupIdFallback(100L, 5L, generic))
                .isInstanceOf(StartupServiceUnavailableException.class)
                .hasMessageContaining("Startup service is temporarily unavailable");
    }
}
