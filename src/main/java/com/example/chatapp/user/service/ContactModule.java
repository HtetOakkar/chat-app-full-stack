package com.example.chatapp.user.service;

import com.example.chatapp.user.model.dto.ContactDto;
import com.example.chatapp.user.model.request.AddContactRequest;

import java.util.List;

public interface ContactModule {
    ContactDto addContact(Long ownerId, AddContactRequest request);
    List<ContactDto> getContacts(Long ownerId);
    void removeContact(Long ownerId, Long contactId);
    List<ContactDto> getMutualContacts(Long userId, Long otherUserId);
    ContactDto sendContactRequest(Long ownerId, String contactUsername);
    ContactDto acceptContactRequest(Long ownerId, Long contactId);
    ContactDto rejectContactRequest(Long ownerId, Long contactId);
    List<ContactDto> getContactRequests(Long ownerId);
    ContactDto blockUser(Long ownerId, Long contactId);
    void unblockUser(Long ownerId, Long contactId);
    List<ContactDto> getBlockedUsers(Long ownerId);
    void acceptRequestIfPending(Long ownerId, Long contactId);
}
