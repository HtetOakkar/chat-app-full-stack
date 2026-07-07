---
status: accepted
---

# Ring Regardless of Callee Presence

When a user initiates a Call, we ring for the full 30-second timeout regardless of whether the callee appears online in the SessionRegistry. We do not short-circuit with a "User is offline" failure.

The obvious alternative is to check `SessionRegistry.isOnline()` before sending the call offer and fail immediately if the callee has no active WebSocket session. This would save the caller from waiting 30 seconds for an answer that will never come. However, the WebSocket presence system has known reliability issues — a connected user can appear offline due to missed heartbeats or stale session state. Gating calls on a signal we know is unreliable would prevent legitimate calls between online users. The 30-second wait is a small cost compared to silently blocking valid calls.
