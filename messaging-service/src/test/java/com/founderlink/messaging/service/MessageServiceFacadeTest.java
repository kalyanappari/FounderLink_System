package com.founderlink.messaging.service;

import com.founderlink.messaging.command.MessageCommandService;
import com.founderlink.messaging.dto.MessageRequestDTO;
import com.founderlink.messaging.dto.MessageResponseDTO;
import com.founderlink.messaging.dto.PagedResponse;
import com.founderlink.messaging.query.MessageQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceFacadeTest {

    @Mock
    private MessageCommandService commandService;

    @Mock
    private MessageQueryService queryService;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("sendMessage - delegates to command service")
    void sendMessage_Delegates() {
        MessageRequestDTO req = new MessageRequestDTO();
        MessageResponseDTO res = MessageResponseDTO.builder().id(1L).build();
        when(commandService.sendMessage(req)).thenReturn(res);

        MessageResponseDTO result = messageService.sendMessage(req);

        assertThat(result).isEqualTo(res);
        verify(commandService).sendMessage(req);
    }

    @Test
    @DisplayName("getMessageById - delegates to query service")
    void getMessageById_Delegates() {
        MessageResponseDTO res = MessageResponseDTO.builder().id(1L).build();
        when(queryService.getMessageById(1L)).thenReturn(res);

        MessageResponseDTO result = messageService.getMessageById(1L);

        assertThat(result).isEqualTo(res);
        verify(queryService).getMessageById(1L);
    }

    @Test
    @DisplayName("getConversation - delegates to query service")
    void getConversation_Delegates() {
        PagedResponse<MessageResponseDTO> res = new PagedResponse<>();
        PageRequest page = PageRequest.of(0, 10);
        when(queryService.getConversation(1L, 2L, page)).thenReturn(res);

        PagedResponse<MessageResponseDTO> result = messageService.getConversation(1L, 2L, page);

        assertThat(result).isEqualTo(res);
        verify(queryService).getConversation(1L, 2L, page);
    }

    @Test
    @DisplayName("getConversationPartners - delegates to query service")
    void getConversationPartners_Delegates() {
        List<Long> res = List.of(2L, 3L);
        when(queryService.getConversationPartners(1L)).thenReturn(res);

        List<Long> result = messageService.getConversationPartners(1L);

        assertThat(result).isEqualTo(res);
        verify(queryService).getConversationPartners(1L);
    }
}
