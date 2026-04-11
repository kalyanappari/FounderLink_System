package com.founderlink.payment.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterQueueHandlerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DeadLetterLogRepository dlqLogRepository;

    @InjectMocks
    private DeadLetterQueueHandler dlqHandler;

    private final ObjectMapper realMapper = new ObjectMapper();

    @Test
    @DisplayName("handleDeadLetterMessage - success with explicit eventType")
    void handleDeadLetterMessage_ExplicitEventType() throws IOException {
        String msg = "{\"eventType\":\"CustomEvent\", \"investmentId\":\"123\", \"investorId\":\"456\"}";
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(any(DeadLetterLog.class));
        assertEquals(1, dlqHandler.getStats().totalMessagesReceived());
        assertEquals(1, dlqHandler.getStats().successfullyLogged());
    }

    @Test
    @DisplayName("handleDeadLetterMessage - infer RejectedEvent")
    void handleDeadLetterMessage_InferRejected() throws IOException {
        String msg = "{\"investmentId\":\"123\", \"rejectionReason\":\"failed\"}";
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(argThat(log -> "InvestmentRejectedEvent".equals(log.getEventType())));
    }

    @Test
    @DisplayName("handleDeadLetterMessage - infer CreatedEvent")
    void handleDeadLetterMessage_InferCreated() throws IOException {
        String msg = "{\"investmentId\":\"123\", \"amount\":500}";
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(argThat(log -> "InvestmentCreatedEvent".equals(log.getEventType())));
    }

    @Test
    @DisplayName("handleDeadLetterMessage - generic unknown")
    void handleDeadLetterMessage_UnknownType() throws IOException {
        String msg = "{\"something\":\"else\"}";
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(argThat(log -> "UNKNOWN".equals(log.getEventType())));
    }

    @Test
    @DisplayName("handleDeadLetterMessage - JSON exception")
    void handleDeadLetterMessage_JsonException() throws IOException {
        String msg = "invalid json";
        when(objectMapper.readTree(msg)).thenThrow(new RuntimeException("parse error"));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, never()).save(any());
        assertEquals(1, dlqHandler.getStats().processingErrors());
    }

    @Test
    @DisplayName("handleDeadLetterMessage - ID missing fallbacks")
    void handleDeadLetterMessage_NoIds() throws IOException {
        String msg = "{\"eventType\":\"NoIdsEvent\"}";
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(argThat(log -> 
            "N/A".equals(log.getInvestmentId()) && "N/A".equals(log.getInvestorId())
        ));
    }
    @Test
    @DisplayName("handleDeadLetterMessage - Partial infer (InvestmentId only)")
    void handleDeadLetterMessage_PartialInfer() throws IOException {
        String msg = "{\"investmentId\":\"123\"}"; // hits line 108 branch T,F
        when(objectMapper.readTree(msg)).thenReturn(realMapper.readTree(msg));

        dlqHandler.handleDeadLetterMessage(msg);

        verify(dlqLogRepository, times(1)).save(argThat(log -> "UNKNOWN".equals(log.getEventType())));
    }
}
