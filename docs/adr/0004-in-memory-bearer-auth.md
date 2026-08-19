# ADR-0004: Persistent Bearer Token Browser Auth

## Status

Accepted

## Context

The chat web application uses JWT bearer tokens for REST requests and STOMP WebSocket connection headers. Previously, storing tokens in memory only caused users to be abruptly logged out upon page reload and triggered unauthorized runtime errors in Next.js when reloading workspace routes.

## Decision

The browser persists the bearer access token in `localStorage` so user sessions remain active across page reloads.

- Login stores the token in `localStorage` and memory.
- REST requests send `Authorization: Bearer <token>` from the token source.
- STOMP WebSocket connections continue sending `Authorization: Bearer <token>` from authenticated React state.
- Logout clears the stored token from `localStorage` and memory.
- Page reloads and initial client mount restore authentication from `localStorage` without unneeded logouts.
- Workspace routes guard initial API calls until authentication status has loaded.

## Consequences

Preserves the user's active session across page reloads and browser refreshes while avoiding runtime errors on deep routes. A future refresh-token flow can add an HttpOnly refresh cookie when needed.
