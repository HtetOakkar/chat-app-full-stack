package com.example.chatapp.user.service;

import com.example.chatapp.user.model.dto.ContactDto;
import com.example.chatapp.user.model.request.AddContactRequest;

import java.util.List;

public interface ContactService {
    ContactDto addContact(Long ownerId, AddContactRequest request);
    List<ContactDto> getContacts(Long ownerId);
    void removeContact(Long ownerId, Long contactId);
    ContactDto acceptRequest(Long ownerId, Long contactId);
    ContactDto blockUser(Long ownerId, Long contactId);
    ContactDto neglectRequest(Long ownerId, Long contactId);
    List<ContactDto> getPendingRequests(Long ownerId);
    void acceptRequestIfPending(Long ownerId, Long contactId);
}
