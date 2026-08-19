package com.example.chatapp.performance;

import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.service.MessageServiceImpl;
import com.example.chatapp.workspace.repository.ConversationParticipantRepository;
import com.example.chatapp.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackendPerformanceGuardrailTest {

    @Test
    void publicHistoryUsesBoundedRedisCursorLookupInsteadOfFullPendingScan() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        RedisMessageRepository redisMessageRepository = mock(RedisMessageRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
        ConversationParticipantRepository conversationParticipantRepository = mock(ConversationParticipantRepository.class);
        MessageServiceImpl service = new MessageServiceImpl(
                messageRepository,
                messageMapper,
                redisMessageRepository,
                workspaceMemberRepository,
                conversationParticipantRepository);

        when(redisMessageRepository.findPublicMessagesBefore(isNull(), isNull(), eq(25))).thenReturn(List.of());

        MessagePage page = service.getPublicMessages(null, 25);

        assertNotNull(page);
        verify(redisMessageRepository).findPublicMessagesBefore(isNull(), isNull(), eq(25));
        verify(redisMessageRepository, never()).peekMessages();
    }
}
