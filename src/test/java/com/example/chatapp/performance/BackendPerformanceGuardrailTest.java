package com.example.chatapp.performance;

import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.service.MessageServiceImpl;
import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.user.service.ContactModuleImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

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
        ContactRepository contactRepository = mock(ContactRepository.class);
        MessageServiceImpl service = new MessageServiceImpl(
                messageRepository,
                messageMapper,
                redisMessageRepository,
                contactRepository);

        when(messageRepository.findPublicMessages(isNull(), isNull(), any(Pageable.class))).thenReturn(List.of());
        when(redisMessageRepository.findPublicMessagesBefore(isNull(), isNull(), eq(25))).thenReturn(List.of());

        MessagePage page = service.getPublicMessages(null, 25);

        assertNotNull(page);
        verify(redisMessageRepository).findPublicMessagesBefore(isNull(), isNull(), eq(25));
        verify(redisMessageRepository, never()).peekMessages();
    }

    @Test
    void contactListEnrichmentUsesBatchInputsInsteadOfPerContactLatestAndUnreadQueries() {
        ContactRepository contactRepository = mock(ContactRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        RedisMessageRepository redisMessageRepository = mock(RedisMessageRepository.class);
        ContactModuleImpl contactModule = new ContactModuleImpl(
                contactRepository,
                userRepository,
                messageRepository,
                redisMessageRepository);

        User owner = User.builder().id(1L).username("owner").build();
        User firstContact = User.builder().id(2L).username("first").build();
        User secondContact = User.builder().id(3L).username("second").build();
        List<Contact> contacts = List.of(
                Contact.builder().id(10L).owner(owner).contactUser(firstContact).status(ContactStatus.ACCEPTED).build(),
                Contact.builder().id(11L).owner(owner).contactUser(secondContact).status(ContactStatus.ACCEPTED).build());

        when(contactRepository.findContactsNative(owner.getId())).thenReturn(contacts);
        when(messageRepository.findConversationMessagesForContacts(owner.getId(), List.of(2L, 3L))).thenReturn(List.of());
        when(redisMessageRepository.peekMessages()).thenReturn(List.of());

        contactModule.getContacts(owner.getId());

        verify(messageRepository).findConversationMessagesForContacts(owner.getId(), List.of(2L, 3L));
        verify(redisMessageRepository).peekMessages();
        verify(messageRepository, never()).countUnreadMessages(anyLong(), anyLong(), any());
        verify(messageRepository, never()).findLatestMessageBetweenUsers(anyLong(), anyLong(), any(), any());
    }
}
