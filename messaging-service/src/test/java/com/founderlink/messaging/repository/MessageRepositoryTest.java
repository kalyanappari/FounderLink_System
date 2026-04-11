package com.founderlink.messaging.repository;

import com.founderlink.messaging.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();

        // Seed mock data: conversation between user 100 and user 200
        Message msg1 = new Message();
        msg1.setSenderId(100L);
        msg1.setReceiverId(200L);
        msg1.setContent("Hey, are you interested in our startup?");
        messageRepository.save(msg1);

        Message msg2 = new Message();
        msg2.setSenderId(200L);
        msg2.setReceiverId(100L);
        msg2.setContent("Yes! Tell me more about the equity split.");
        messageRepository.save(msg2);

        Message msg3 = new Message();
        msg3.setSenderId(100L);
        msg3.setReceiverId(200L);
        msg3.setContent("We offer 10% for early team members.");
        messageRepository.save(msg3);

        // Separate conversation: user 100 and user 300
        Message msg4 = new Message();
        msg4.setSenderId(300L);
        msg4.setReceiverId(100L);
        msg4.setContent("Can we schedule a call?");
        messageRepository.save(msg4);
    }

    @Test
    @DisplayName("findConversation - returns messages between two users in order")
    void findConversation_ReturnsBidirectionalMessages() {
        Page<Message> conversation = messageRepository.findConversation(100L, 200L, PageRequest.of(0, 10));

        assertThat(conversation.getContent()).hasSize(3);
        assertThat(conversation.getContent().get(0).getContent()).isEqualTo("We offer 10% for early team members.");
        assertThat(conversation.getContent().get(1).getContent()).isEqualTo("Yes! Tell me more about the equity split.");
        assertThat(conversation.getContent().get(2).getContent()).isEqualTo("Hey, are you interested in our startup?");
    }

    @Test
    @DisplayName("findConversation - works regardless of parameter order")
    void findConversation_SymmetricParameterOrder() {
        Page<Message> conversation1 = messageRepository.findConversation(100L, 200L, PageRequest.of(0, 10));
        Page<Message> conversation2 = messageRepository.findConversation(200L, 100L, PageRequest.of(0, 10));

        assertThat(conversation1.getContent()).hasSameSizeAs(conversation2.getContent());
    }

    @Test
    @DisplayName("findConversation - returns empty for non-existent conversation")
    void findConversation_WhenNoMessages_ReturnsEmpty() {
        Page<Message> conversation = messageRepository.findConversation(200L, 300L, PageRequest.of(0, 10));

        assertThat(conversation.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findConversationPartners - returns all unique partner IDs")
    void findConversationPartners_ReturnsDistinctPartners() {
        List<Long> partners = messageRepository.findConversationPartners(100L);

        assertThat(partners).containsExactlyInAnyOrder(200L, 300L);
    }

    @Test
    @DisplayName("findConversationPartners - returns empty when user has no messages")
    void findConversationPartners_WhenNoMessages_ReturnsEmpty() {
        List<Long> partners = messageRepository.findConversationPartners(999L);

        assertThat(partners).isEmpty();
    }

    @Test
    @DisplayName("findConversationPartners - user as only receiver still shows partners")
    void findConversationPartners_AsReceiver_ShowsSender() {
        List<Long> partners = messageRepository.findConversationPartners(300L);

        assertThat(partners).containsExactly(100L);
    }
}
