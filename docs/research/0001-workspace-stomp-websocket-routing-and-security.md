# Research: Workspace STOMP WebSocket Routing, Subscription Authorization & Redis Pub/Sub Architecture

**Ticket:** Wayfinder #63  
**Status:** Complete  
**Date:** 2026-08-18  
**Domain Alignment:** `CONTEXT.md` (Workspace, Workspace Member, Workspace Role, Channel, Public/Private Channel, Direct Message, Group Direct Message, Presence, Call, Typing Indicator)

---

## 1. Executive Summary & Problem Statement

### 1.1 Context
The existing WebSocket implementation in this repository operates on a single-tenant, unpartitioned paradigm:
- Global public channel: `/topic/public`
- User-specific queues: `/user/queue/messages`, `/user/queue/requests`, `/user/queue/online`, `/user/queue/typing`, `/user/queue/call`
  - *Update (2026-09-29, #89):* `/user/queue/channel-removals` was added later as a deliberate exception to Workspace-scoped user queues. See ADR-0007 and the table in 2.2.
- Single Redis Pub/Sub channel: `websocket:messages`
- Interceptor authorization: Authentication only (`AuthChannelInterceptor` checks JWT on `CONNECT`, but does not inspect `SUBSCRIBE` or `SEND` destinations).

### 1.2 The Problem
As the application evolves into an isolated multi-tenant **Workspace** platform:
1. **Lack of Subscription Authorization (IDOR / Data Leakage):** Any authenticated user can subscribe to any topic or queue (e.g. `/topic/workspace.{workspaceId}.channel.{channelId}`) without the backend verifying whether the user is a `Workspace Member` or has access to a `Private Channel`.
2. **Sender Identity & Spoofing:** Inbound `SEND` frames and `@MessageMapping` handlers must enforce that the sender belongs to the target workspace and cannot spoof message properties or broadcast to unauthorized channels.
3. **Cluster Fanout & Scalability:** A single Redis topic (`websocket:messages`) broadcasts all workspace events to all application nodes, causing $O(N_{\text{nodes}} \times M_{\text{messages}})$ deserialization overhead across all tenants.
4. **Error Handling & Feedback:** Throwing raw exceptions during STOMP frame processing can abruptly drop WebSocket connections or get swallowed in async channels without clear error codes sent to the client.

---

## 2. STOMP Destination Path Topology

### 2.1 Destination Prefixes Configuration
Spring WebSocket Message Broker configuration (`WebSocketMessageBrokerConfigurer`):
- **Application Inbound Prefix:** `/app` (routes to `@Controller` / `@MessageMapping` handlers)
- **Broker Destination Prefixes:** `/topic` (broadcast / pub-sub), `/queue` (point-to-point / user-specific)
- **User Destination Prefix:** `/user` (translated by `UserDestinationMessageHandler` to session-specific queues)

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setUserDestinationPrefix("/user");
}
```

### 2.2 Concrete Destination Patterns

| Feature / Scope | Client SUBSCRIBE Destination | Client SEND Destination (Inbound) | Server Broadcast Destination |
| :--- | :--- | :--- | :--- |
| **Public / Private Channel Messages** | `/topic/workspace.{workspaceId}.channel.{channelId}` | `/app/workspace.{workspaceId}.channel.{channelId}.send` | `/topic/workspace.{workspaceId}.channel.{channelId}` |
| **Channel Typing Indicator** | `/topic/workspace.{workspaceId}.channel.{channelId}.typing` | `/app/workspace.{workspaceId}.channel.{channelId}.typing` | `/topic/workspace.{workspaceId}.channel.{channelId}.typing` |
| **Workspace Presence** | `/topic/workspace.{workspaceId}.presence` | `/app/workspace.{workspaceId}.presence` | `/topic/workspace.{workspaceId}.presence` |
| **Workspace Lifecycle Events** *(Members joined/removed, role updates)* | `/topic/workspace.{workspaceId}.events` | Server-initiated only (via admin REST / events) | `/topic/workspace.{workspaceId}.events` |
| **User Direct Messages (DM / Group DM)** | `/user/queue/workspace.{workspaceId}.messages` | `/app/workspace.{workspaceId}.dm.send` | `/user/{userId}/queue/workspace.{workspaceId}.messages` |
| **User DM Typing Indicator** | `/user/queue/workspace.{workspaceId}.typing` | `/app/workspace.{workspaceId}.dm.typing` | `/user/{userId}/queue/workspace.{workspaceId}.typing` |
| **User Notifications & Invites** | `/user/queue/workspace.{workspaceId}.notifications` | Server-initiated only | `/user/{userId}/queue/workspace.{workspaceId}.notifications` |
| **Call Signaling** *(WebRTC 1:1 Call)* | `/user/queue/workspace.{workspaceId}.calls` | `/app/workspace.{workspaceId}.call.{action}` | `/user/{userId}/queue/workspace.{workspaceId}.calls` |
| **Channel Removal Notice** *(exception: not Workspace-scoped, see ADR-0007)* | `/user/queue/channel-removals` | Server-initiated only | `/user/{userId}/queue/channel-removals` |

---

## 3. Spring ChannelInterceptor: Subscription & Send Authorization Architecture

### 3.1 Interception Pipeline
Spring STOMP processing executes through `clientInboundChannel`. Interceptors registered via `configureClientInboundChannel(registration)` intercept every inbound message before it reaches the message broker or `@MessageMapping` handlers.

```
Client WebSocket Frame
         │
         ▼
[ WebSocketHandler / StompSubProtocolHandler ]
         │ (Translates STOMP frame to Spring Message<byte[]>)
         ▼
[ clientInboundChannel: ChannelInterceptor.preSend() ]
    ├── StompCommand.CONNECT    ──> Validate JWT & Populate UserPrincipal
    ├── StompCommand.SUBSCRIBE  ──> Authorize Workspace & Channel Membership
    ├── StompCommand.SEND       ──> Validate Sender Membership & Anti-Spoofing
    └── StompCommand.DISCONNECT ──> Session Cleanup & Presence
         │ (If approved: returns message; If rejected: throws AccessDeniedException)
         ▼
[ UserDestinationMessageHandler / SimpAnnotationMethodMessageHandler / SimpleBroker ]
```

### 3.2 Authorization Rules Matrix

| STOMP Command | Destination Pattern | Required Verification |
| :--- | :--- | :--- |
| `CONNECT` | N/A | Valid JWT in `Authorization` header, non-expired, email verified. |
| `SUBSCRIBE` | `/topic/workspace.{wId}.channel.{cId}` | 1. User is an active `Workspace Member` of `wId`.<br>2. If channel is `Public`, membership in `wId` is sufficient.<br>3. If channel is `Private`, user must be an explicit member of `cId`. |
| `SUBSCRIBE` | `/topic/workspace.{wId}.presence`<br>`/topic/workspace.{wId}.events` | User is an active `Workspace Member` of `wId`. |
| `SUBSCRIBE` | `/user/queue/workspace.{wId}.*` | User is an active `Workspace Member` of `wId`. |
| `SUBSCRIBE` | `/user/queue/channel-removals` | None beyond authentication. The path has no Workspace, and user-destination resolution confines it to the subscriber's own sessions (ADR-0007). |
| `SUBSCRIBE` | `/queue/workspace.{wId}.user.{userId}` *(direct queue path)* | 1. `userId == principal.getId()` (anti-hijacking).<br>2. User is an active `Workspace Member` of `wId`. |
| `SEND` | `/app/workspace.{wId}.channel.{cId}.*` | 1. User is an active `Workspace Member` of `wId`.<br>2. User has write permission in `cId` (not muted, channel not archived).<br>3. `senderId` matches `principal.getId()`. |
| `SEND` | `/app/workspace.{wId}.dm.*` | 1. Sender and Recipient are both active `Workspace Members` of `wId`.<br>2. Sender is not blocked by Recipient. |

---

## 4. Redis Pub/Sub Topology for Clustered Scale-Out

### 4.1 Topology Comparison

| Strategy | Channel Structure | Routing Logic | Pros | Cons | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Option A: Monolithic Redis Channel** | `websocket:messages` | All nodes receive all messages; filter by destination/user locally. | Minimal Redis configuration; single subscriber container. | Severe CPU & network waste: all nodes deserialize messages for all workspaces. | ❌ Inefficient for multi-workspace scale. |
| **Option B: Fine-Grained Redis Channels** | `redis:topic:workspace.{wId}.channel.{cId}`<br>`redis:user:{userId}` | Subscriptions created/removed dynamically per active local room. | Minimal broadcast waste; strict partition isolation. | High Redis listener management complexity in Spring. | ⚠️ High operational complexity. |
| **Option C: Workspace-Partitioned Redis Channels (Recommended)** | `redis:ws:workspace.{wId}`<br>`redis:ws:user:{userId}` | Topics routed via workspace channel; user DMs routed via user channel. | Optimal balance: nodes subscribe to `PatternTopic("redis:ws:workspace.*")` or active workspace set, DMs route directly to target user channel. | Requires standardized Redis message envelope. | ✅ **Recommended Architecture** |

---

## 5. Performance, Session Caching & Cache Invalidation

1. **Tier 1 (WebSocket Session Attributes):** Cached in `StompHeaderAccessor.getSessionAttributes()` during the STOMP connection.
2. **Tier 2 (Distributed Redis Cache):** `cache:workspace:{workspaceId}:user:{userId}:membership` with a 10-minute TTL.
3. **Cache Invalidation on Membership Revocation:** An admin eviction event on `redis:ws:events:eviction` prompts cluster nodes to disconnect or update active STOMP sessions for the revoked user.
