package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.Contact;
import com.example.chatapp.user.model.entity.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = "SELECT * FROM contacts WHERE owner_id = :ownerId AND status IN ('CONTACT', 'ACCEPTED')", nativeQuery = true)
    List<Contact> findContactsNative(@Param("ownerId") Long ownerId);

    @Query(value = "SELECT * FROM contacts WHERE owner_id = :ownerId AND status = 'BLOCKED'", nativeQuery = true)
    List<Contact> findBlockedContactsNative(@Param("ownerId") Long ownerId);

    @Query(value = "SELECT c1.* FROM contacts c1 " +
                   "WHERE c1.owner_id = :userId AND c1.status IN ('CONTACT', 'ACCEPTED') " +
                   "AND EXISTS (SELECT 1 FROM contacts c2 " +
                   "            WHERE c2.owner_id = :otherUserId AND c2.status IN ('CONTACT', 'ACCEPTED') " +
                   "            AND c2.contact_id = c1.contact_id)",
           nativeQuery = true)
    List<Contact> findMutualContactsNative(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
