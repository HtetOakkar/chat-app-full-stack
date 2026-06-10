package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByUsernameContainingIgnoreCase(String keyword);
    List<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(String usernameKeyword, String fullNameKeyword);
}

