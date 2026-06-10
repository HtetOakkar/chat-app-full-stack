package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByOwnerId(Long ownerId);
    List<Contact> findByOwnerIdAndStatus(Long ownerId, ContactStatus status);
    List<Contact> findByOwnerIdAndStatusIn(Long ownerId, List<ContactStatus> statuses);
    Optional<Contact> findByOwnerIdAndContactUserId(Long ownerId, Long contactId);
    boolean existsByOwnerIdAndContactUserId(Long ownerId, Long contactId);
    boolean existsByOwnerIdAndContactUserIdAndStatus(Long ownerId, Long contactId, ContactStatus status);
}
