package com.example.chatapp.jwt;

import com.example.chatapp.jwt.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private String getToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.replace("Bearer ", "");
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getToken(request);

            if (jwt != null && jwtService.validateToken(jwt)) {
                String username = jwtService.extractUsername(jwt);
                Claims claims = jwtService.getClaims(jwt);
                String roles = claims.get("roles", String.class);
                String userIdClaim = claims.getId();

                if (roles == null || roles.isBlank() || userIdClaim == null) {
                    log.warn("Skipping authentication due to incomplete JWT claims");
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = Long.parseLong(userIdClaim);
                List<String> authorityArray = Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .toList();
                List<GrantedAuthority> authorities = authorityArray.stream()
                        .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                if (authorities.isEmpty()) {
                    authorities = Collections.emptyList();
                }
                String fullName = claims.get("fullName", String.class);
                UserPrincipal userPrincipal = new UserPrincipal(userId, username, null, fullName, authorities);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("JWT authentication failed", e);
        }
        filterChain.doFilter(request, response);
    }




}
