# ADR-0004: In-Memory Bearer Token Browser Auth

## Status

Accepted

## Context

The Security Hardening PRD requires browser authentication to avoid persistent JWT storage in `localStorage`. The application already uses JWT bearer tokens for REST requests and STOMP WebSocket connection headers, and the product direction is to keep bearer token auth for future expansion.

## Decision

The browser keeps the bearer access token in memory only.

- Login stores the token in React/module memory.
- REST requests send `Authorization: Bearer <token>` from the in-memory token source.
- STOMP WebSocket connections continue sending `Authorization: Bearer <token>` from authenticated React state.
- Logout clears the in-memory token and client-visible authenticated state.
- Browser reloads require a new login because no token is restored from durable browser storage.

## Consequences

This removes common XSS token-theft persistence through `localStorage` while preserving the bearer token contract for backend APIs and WebSocket authentication. A future refresh-token flow can add an HttpOnly refresh cookie without changing the REST/STOMP bearer access-token header contract.
