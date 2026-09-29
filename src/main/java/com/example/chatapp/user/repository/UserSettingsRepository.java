package com.example.chatapp.user.repository;

import com.example.chatapp.user.model.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Settings are fetched through here, so loading a User never loads their settings as a side effect. */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);
}
