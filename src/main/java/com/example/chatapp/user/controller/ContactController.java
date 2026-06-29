package com.example.chatapp.user.controller;

import com.example.chatapp.exception.BadRequestException;
import com.example.chatapp.jwt.UserPrincipal;
import com.example.chatapp.user.model.dto.ContactDto;
import com.example.chatapp.user.model.request.AddContactRequest;
import com.example.chatapp.user.service.ContactModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactModule contactModule;

    @PostMapping
    public ResponseEntity<ContactDto> addContact(@Valid @RequestBody AddContactRequest request,
                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        ContactDto contactDto = contactModule.addContact(currentUser.getId(), request);
        return new ResponseEntity<>(contactDto, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ContactDto>> getContacts(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.getContacts(currentUser.getId()));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> removeContact(@PathVariable Long contactId,
                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        contactModule.removeContact(currentUser.getId(), contactId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{contactId}/accept")
    public ResponseEntity<ContactDto> acceptRequest(@PathVariable Long contactId,
                                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.acceptContactRequest(currentUser.getId(), contactId));
    }

    @PutMapping("/{contactId}/block")
    public ResponseEntity<ContactDto> blockUser(@PathVariable Long contactId,
                                                @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.blockUser(currentUser.getId(), contactId));
    }

    @PutMapping("/{contactId}/unblock")
    public ResponseEntity<Void> unblockUser(@PathVariable Long contactId,
                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        contactModule.unblockUser(currentUser.getId(), contactId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{contactId}/neglect")
    public ResponseEntity<ContactDto> neglectRequest(@PathVariable Long contactId,
                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.rejectContactRequest(currentUser.getId(), contactId));
    }

    @GetMapping("/blocked")
    public ResponseEntity<List<ContactDto>> getBlockedContacts(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.getBlockedUsers(currentUser.getId()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<ContactDto>> getPendingRequests(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.getContactRequests(currentUser.getId()));
    }

    @GetMapping("/mutual/{otherUserId}")
    public ResponseEntity<List<ContactDto>> getMutualContacts(@PathVariable Long otherUserId,
                                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("User not authenticated");
        }
        return ResponseEntity.ok(contactModule.getMutualContacts(currentUser.getId(), otherUserId));
    }
}
