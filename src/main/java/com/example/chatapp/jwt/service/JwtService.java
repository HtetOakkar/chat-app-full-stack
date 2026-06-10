package com.example.chatapp.jwt.service;

import com.example.chatapp.user.model.dto.UserDto;
import com.example.chatapp.user.model.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;

public interface JwtService {
    String generateToken(Authentication authentication);
    String generateTokenFromUser(User user);
    boolean validateToken(String token);
    String extractUsername(String token);
    Claims getClaims(String jwt);
}
