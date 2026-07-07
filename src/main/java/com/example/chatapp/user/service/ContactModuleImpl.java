package com.example.chatapp.user.service;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.exception.ConflictException;
import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.user.model.dto.ContactDto;
import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.ContactStatus;
import com.example.chatapp.user.model.request.AddContactRequest;
import com.example.chatapp.user.repository.ContactRepository;
import com.example.chatapp.user.repository.UserRepository;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactModuleImpl implements ContactModule {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final RedisMessageRepository redisMessageRepository;

    @Override
    @Transactional
    public ContactDto addContact(Long ownerId, AddContactRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));

        User contactUser = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new NotFoundException("User to add not found"));

        if (owner.getId().equals(contactUser.getId())) {
            throw new BadRequestException("You cannot add yourself as a contact");
        }

        if (contactUser.getEmail() != null && !contactUser.isEmailVerified()) {
            throw new BadRequestException("Cannot add unverified user as contact");
        }

        Optional<Contact> existingContactOpt = contactRepository.findByOwnerIdAndContactUserId(owner.getId(), contactUser.getId());
        Contact contact;
        if (existingContactOpt.isPresent()) {
            contact = existingContactOpt.get();
            if (contact.getStatus() == ContactStatus.CONTACT || contact.getStatus() == ContactStatus.ACCEPTED) {
                throw new ConflictException("User is already in your contacts");
            }
            contact.setStatus(ContactStatus.CONTACT);
        } else {
            contact = Contact.builder()
                    .owner(owner)
                    .contactUser(contactUser)
                    .status(ContactStatus.CONTACT)
                    .build();
        }

        // Create/update reverse relation for the contactUser as PENDING_REQUEST
        Optional<Contact> reverseContactOpt = contactRepository.findByOwnerIdAndContactUserId(contactUser.getId(), owner.getId());
        if (reverseContactOpt.isEmpty()) {
            Contact reverseContact = Contact.builder()
                    .owner(contactUser)
                    .contactUser(owner)
                    .status(ContactStatus.PENDING_REQUEST)
                    .build();
            contactRepository.save(reverseContact);
        }

        Contact saved = contactRepository.save(contact);
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDto> getContacts(Long ownerId) {
        return contactRepository.findContactsNative(ownerId).stream()
                .map(contact -> mapToEnrichedDto(contact, ownerId))
                .filter(dto -> dto.getClearedAt() == null || dto.getLastMessageTimestamp() != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeContact(Long ownerId, Long contactId) {
        Contact contact = contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId)
                .orElseThrow(() -> new NotFoundException("Contact not found"));
        contactRepository.delete(contact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDto> getMutualContacts(Long userId, Long otherUserId) {
        return contactRepository.findMutualContactsNative(userId, otherUserId).stream()
                .map(contact -> mapToEnrichedDto(contact, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ContactDto sendContactRequest(Long ownerId, String contactUsername) {
        AddContactRequest request = new AddContactRequest();
        request.setUsername(contactUsername);
        return addContact(ownerId, request);
    }

    @Override
    @Transactional
    public ContactDto acceptContactRequest(Long ownerId, Long contactId) {
        Contact contact = contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId)
                .orElseThrow(() -> new NotFoundException("Contact request not found"));
        if (contact.getStatus() != ContactStatus.PENDING_REQUEST) {
            throw new BadRequestException("No pending contact request found to accept");
        }
        contact.setStatus(ContactStatus.ACCEPTED);
        Contact saved = contactRepository.save(contact);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public ContactDto rejectContactRequest(Long ownerId, Long contactId) {
        Contact contact = contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId)
                .orElseThrow(() -> new NotFoundException("Contact request not found"));
        if (contact.getStatus() != ContactStatus.PENDING_REQUEST) {
            throw new BadRequestException("No pending contact request found to neglect");
        }
        contact.setStatus(ContactStatus.NEGLECTED);
        Contact saved = contactRepository.save(contact);
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDto> getContactRequests(Long ownerId) {
        return contactRepository.findByOwnerIdAndStatus(ownerId, ContactStatus.PENDING_REQUEST).stream()
                .map(contact -> mapToEnrichedDto(contact, ownerId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ContactDto blockUser(Long ownerId, Long contactId) {
        if (ownerId.equals(contactId)) {
            throw new NotFoundException("Cannot block yourself");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        User contactUser = userRepository.findById(contactId)
                .orElseThrow(() -> new NotFoundException("User to block not found"));

        Contact contact = contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId)
                .orElse(Contact.builder()
                        .owner(owner)
                        .contactUser(contactUser)
                        .build());

        contact.setStatus(ContactStatus.BLOCKED);
        Contact saved = contactRepository.save(contact);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void unblockUser(Long ownerId, Long contactId) {
        Contact contact = contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId)
                .orElseThrow(() -> new NotFoundException("Block relation not found"));
        if (contact.getStatus() != ContactStatus.BLOCKED) {
            throw new BadRequestException("User is not blocked");
        }
        contactRepository.delete(contact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDto> getBlockedUsers(Long ownerId) {
        return contactRepository.findBlockedContactsNative(ownerId).stream()
                .map(contact -> mapToEnrichedDto(contact, ownerId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void acceptRequestIfPending(Long ownerId, Long contactId) {
        contactRepository.findByOwnerIdAndContactUserId(ownerId, contactId).ifPresent(contact -> {
            if (contact.getStatus() == ContactStatus.PENDING_REQUEST || contact.getStatus() == ContactStatus.NEGLECTED) {
                contact.setStatus(ContactStatus.ACCEPTED);
                contactRepository.save(contact);
            }
        });
    }

    private ContactDto mapToEnrichedDto(Contact contact, Long ownerId) {
        Long contactUserId = contact.getContactUser().getId();
        Instant clearedAt = contact.getClearedAt();

        // 1. Calculate unread count (DB + Redis)
        long dbUnreadCount = messageRepository.countUnreadMessages(contactUserId, ownerId, clearedAt);
        long redisUnreadCount = redisMessageRepository.peekMessages().stream()
                .filter(msg -> msg.getSenderId().equals(contactUserId) &&
                        msg.getRecipientId() != null &&
                        msg.getRecipientId().equals(ownerId) &&
                        (msg.getIsRead() == null || !msg.getIsRead()) &&
                        (clearedAt == null || msg.getTimestamp().isAfter(clearedAt)))
                .count();
        long unreadCount = dbUnreadCount + redisUnreadCount;

        // 2. Determine latest message content & timestamp
        // From DB
        List<Message> dbLatestList = messageRepository.findLatestMessageBetweenUsers(ownerId, contactUserId, clearedAt, PageRequest.of(0, 1));
        Message dbLatest = dbLatestList.isEmpty() ? null : dbLatestList.get(0);

        // From Redis
        com.example.chatapp.message.model.dto.MessageDto redisLatest = redisMessageRepository.peekMessages().stream()
                .filter(msg -> msg.getRecipientId() != null &&
                        ((msg.getSenderId().equals(ownerId) && msg.getRecipientId().equals(contactUserId)) ||
                         (msg.getSenderId().equals(contactUserId) && msg.getRecipientId().equals(ownerId))) &&
                        (clearedAt == null || msg.getTimestamp().isAfter(clearedAt)))
                .findFirst()
                .orElse(null);

        String lastMessageContent = null;
        Instant lastMessageTimestamp = null;
        Long lastMessageSenderId = null;

        if (dbLatest != null && redisLatest != null) {
            if (redisLatest.getTimestamp().isAfter(dbLatest.getSentAt())) {
                lastMessageContent = formatMessageContent(redisLatest.getContent(), redisLatest.getMessageType(), redisLatest.getCallOutcome());
                lastMessageTimestamp = redisLatest.getTimestamp();
                lastMessageSenderId = redisLatest.getSenderId();
            } else {
                lastMessageContent = formatMessageContent(dbLatest.getContent(), dbLatest.getMessageType(), dbLatest.getCallOutcome());
                lastMessageTimestamp = dbLatest.getSentAt();
                lastMessageSenderId = dbLatest.getSender().getId();
            }
        } else if (dbLatest != null) {
            lastMessageContent = formatMessageContent(dbLatest.getContent(), dbLatest.getMessageType(), dbLatest.getCallOutcome());
            lastMessageTimestamp = dbLatest.getSentAt();
            lastMessageSenderId = dbLatest.getSender().getId();
        } else if (redisLatest != null) {
            lastMessageContent = formatMessageContent(redisLatest.getContent(), redisLatest.getMessageType(), redisLatest.getCallOutcome());
            lastMessageTimestamp = redisLatest.getTimestamp();
            lastMessageSenderId = redisLatest.getSenderId();
        }

        return ContactDto.builder()
                .id(contact.getId())
                .contactUserId(contactUserId)
                .contactUsername(contact.getContactUser().getUsername())
                .contactFullName(contact.getContactUser().getFullName())
                .status(contact.getStatus())
                .createdAt(contact.getCreatedAt())
                .clearedAt(clearedAt)
                .lastMessageContent(lastMessageContent)
                .lastMessageTimestamp(lastMessageTimestamp)
                .lastMessageSenderId(lastMessageSenderId)
                .unreadCount(unreadCount)
                .build();
    }

    private ContactDto mapToDto(Contact contact) {
        return ContactDto.builder()
                .id(contact.getId())
                .contactUserId(contact.getContactUser().getId())
                .contactUsername(contact.getContactUser().getUsername())
                .contactFullName(contact.getContactUser().getFullName())
                .status(contact.getStatus())
                .createdAt(contact.getCreatedAt())
                .clearedAt(contact.getClearedAt())
                .build();
    }

    private String formatMessageContent(String content, MessageType messageType, String callOutcome) {
        if (messageType == MessageType.AUDIO || messageType == MessageType.VIDEO || callOutcome != null || (content != null && content.startsWith("{\"outcome\":"))) {
            String outcome = callOutcome;
            if (outcome == null && content != null) {
                if (content.contains("\"outcome\":\"completed\"")) outcome = "completed";
                else if (content.contains("\"outcome\":\"missed\"")) outcome = "missed";
                else if (content.contains("\"outcome\":\"rejected\"")) outcome = "rejected";
                else if (content.contains("\"outcome\":\"cancelled\"")) outcome = "cancelled";
            }
            if (outcome == null) return "Call";
            return switch (outcome.toLowerCase()) {
                case "completed" -> "Call Ended";
                case "missed" -> "Missed Call";
                case "rejected" -> "Call Declined";
                case "cancelled" -> "Call Cancelled";
                default -> "Call";
            };
        }
        return content;
    }
}
