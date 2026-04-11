package com.founderlink.team.serviceImpl;

import com.founderlink.team.command.InvitationCommandService;
import com.founderlink.team.dto.request.InvitationRequestDto;
import com.founderlink.team.dto.response.InvitationResponseDto;
import com.founderlink.team.query.InvitationQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private InvitationCommandService commandService;

    @Mock
    private InvitationQueryService queryService;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    @Test
    void sendInvitation_ShouldDelegateToCommandService() {
        InvitationRequestDto dto = new InvitationRequestDto();
        InvitationResponseDto response = new InvitationResponseDto();
        when(commandService.sendInvitation(5L, dto)).thenReturn(response);

        InvitationResponseDto result = invitationService.sendInvitation(5L, dto);

        assertThat(result).isEqualTo(response);
        verify(commandService).sendInvitation(5L, dto);
    }

    @Test
    void cancelInvitation_ShouldDelegateToCommandService() {
        InvitationResponseDto response = new InvitationResponseDto();
        when(commandService.cancelInvitation(1L, 5L)).thenReturn(response);

        InvitationResponseDto result = invitationService.cancelInvitation(1L, 5L);

        assertThat(result).isEqualTo(response);
        verify(commandService).cancelInvitation(1L, 5L);
    }

    @Test
    void rejectInvitation_ShouldDelegateToCommandService() {
        InvitationResponseDto response = new InvitationResponseDto();
        when(commandService.rejectInvitation(1L, 200L)).thenReturn(response);

        InvitationResponseDto result = invitationService.rejectInvitation(1L, 200L);

        assertThat(result).isEqualTo(response);
        verify(commandService).rejectInvitation(1L, 200L);
    }

    @Test
    void getInvitationsByUserId_ShouldDelegateToQueryService() {
        InvitationResponseDto response = new InvitationResponseDto();
        when(queryService.getInvitationsByUserId(200L)).thenReturn(List.of(response));

        List<InvitationResponseDto> result = invitationService.getInvitationsByUserId(200L);

        assertThat(result).hasSize(1);
        verify(queryService).getInvitationsByUserId(200L);
    }

    @Test
    void getInvitationsByStartupId_ShouldDelegateToQueryService() {
        InvitationResponseDto response = new InvitationResponseDto();
        when(queryService.getInvitationsByStartupId(100L, 5L)).thenReturn(List.of(response));

        List<InvitationResponseDto> result = invitationService.getInvitationsByStartupId(100L, 5L);

        assertThat(result).hasSize(1);
        verify(queryService).getInvitationsByStartupId(100L, 5L);
    }
}
