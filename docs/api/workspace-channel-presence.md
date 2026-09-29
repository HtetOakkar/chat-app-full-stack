# Channel Membership & Presence Sharing API

This page covers the REST and STOMP contracts added or changed by the review fixes for #89 (sub-issues #90–#98): Channel Member addition and removal, the membership events, Presence Sharing, and the static-resource not-found response. Terms follow `CONTEXT.md`.

Every REST endpoint requires `Authorization: Bearer <token>`. Channel endpoints also answer under a `/api/v1/...` alias. For example, `/api/channels/{channelId}/members` and `/api/v1/channels/{channelId}/members` are the same endpoint.

Errors use the shared `ApiError` body: `{timestamp, status, error, message, path}`. For a validation failure, `message` is the first field error. Authorization failures in these services return **401** with a domain message.

---

## 1. Channel Member addition

Adds Workspace Members to a **Private Channel**. The single and batch endpoints share one code path and return the same response.

| Endpoint | Body |
| :--- | :--- |
| `POST /api/channels/{channelId}/members` | `{"userId": 42}` |
| `POST /api/channels/{channelId}/members/batch` | `{"userIds": [42, 43, 44]}` |

**Request rules**

| Rule | `400` message |
| :--- | :--- |
| `userId` present (single) | `userId is required` |
| `userIds` present and non-empty (batch) | `userIds must not be empty` |
| At most 50 `userIds` | `You can add at most 50 colleagues at a time` |
| No `null` elements | `userIds must not contain null` |

Duplicate IDs are collapsed. A user ID that is not a Workspace Member is **reported, not rejected**, whatever the batch size.

**Response**

```json
{
  "added": [42],
  "alreadyMembers": [43],
  "notWorkspaceMembers": [44]
}
```

Each distinct requested ID appears in exactly one list, in request order.

- **`201 Created`**: at least one ID is in `added`.
- **`200 OK`**: nothing was added. The body still reports each category.

**Errors**

| Status | Message | When |
| :--- | :--- | :--- |
| `404` | `Channel not found with ID: {id}` | Unknown Channel. |
| `400` | `Cannot explicitly add members to a public channel` | The Channel is Public. |
| `401` | `Only private channel members can invite colleagues` | The caller is not a Channel Member. |
| `401` | `You are not a member of this workspace` | The caller is not a Workspace Member. |

**Side effects** (only when something was added, and only after commit):

- One consolidated Channel Activity Notice (a `SYSTEM` message) on `/topic/workspace.{workspaceId}.channel.{channelId}`, e.g. `"@alice added @bob, @carol, and @dave to the channel"`.
- A `CHANNEL_MEMBER_ADDED` event (section 3).

---

## 2. Channel Member removal

`DELETE /api/channels/{channelId}/members/{userId}` returns `204 No Content`.

- An **Owner or Admin** may remove any Channel Member. This is a Removal.
- **Any Member** may remove themselves. This is a Voluntary Departure.
- Public Channels have no explicit membership and cannot be left or removed from.

**Errors** (checked in this order)

| Status | Message | When |
| :--- | :--- | :--- |
| `404` | `Channel not found with ID: {id}` | Unknown Channel. |
| `400` | `Cannot remove members from a public channel` | The Channel is Public. |
| `404` | `Target member not found in workspace` | The target is not a Workspace Member. |
| `404` | `User is not a member of this channel` | The target is not a Channel Member. |
| `401` | `You are not a member of this workspace` | The caller is not a Workspace Member. |
| `401` | `Only workspace owners/admins or the member themselves can remove members` | A Member tried to remove someone else. |

**Side effects** (all after commit):

1. **Channel Activity Notice**: `"@member left the channel"`, for both a Removal and a Voluntary Departure. It never names the remover.
2. **Workspace event** on `/topic/workspace.{workspaceId}.events`:

   ```json
   { "type": "CHANNEL_MEMBER_REMOVED", "workspaceId": 1, "channelId": 7, "userId": 42, "isSelfLeave": false }
   ```

   Every Workspace Member receives this event, so it carries no remover fields. `isSelfLeave` tells a Removal from a Voluntary Departure.
3. **Removal Notice** (Removal only), sent to the removed User on `/user/queue/channel-removals`:

   ```json
   {
     "type": "REMOVED_FROM_CHANNEL",
     "workspaceId": 1,
     "channelId": 7,
     "channelName": "design-private",
     "removedByUserId": 3,
     "removedByUsername": "alice",
     "removedByFullName": "Alice Example"
   }
   ```

   `removedByFullName` falls back to the username. This queue is not Workspace-scoped (ADR-0007). Subscribe once per connection, and use `workspaceId` to route the notice. Delivery is live only: a User offline at the time of the Removal never receives it.

---

## 3. Workspace membership events

All of these are published on `/topic/workspace.{workspaceId}.events` **after the transaction commits**. A rolled-back change publishes nothing, so a client that refetches on an event sees the committed state.

| `type` | Fields | Published by |
| :--- | :--- | :--- |
| `CHANNEL_MEMBER_ADDED` | `workspaceId`, `channelId`, `userIds`, `addedByUserId`, and `userId` only when exactly one was added | Channel Member addition (section 1) |
| `CHANNEL_MEMBER_ADDED` | `workspaceId`, `channelId`, `userId` | Invitation acceptance, once per Private initial Channel joined |
| `CHANNEL_MEMBER_REMOVED` | `workspaceId`, `channelId`, `userId`, `isSelfLeave` | Channel Member removal (section 2) |
| `MEMBER_JOINED` | `workspaceId`, `userId`, `username`, `fullName` (falls back to username), `role` | Invitation acceptance by a new Workspace Member |

On invitation acceptance, each Private initial Channel also gets a `"@member joined the channel"` Channel Activity Notice.

---

## 4. Presence Sharing

**Visibility rule:** a User's Presence is decided by **that User's** Presence Sharing choice, never the viewer's. A User with sharing off appears `OFFLINE` to every other Member on every surface: live Presence broadcasts, `GET /api/v1/users/online`, and the `presenceStatus` of member lists (Channel members, DMs, and `GET /api/workspaces/{workspaceId}/members`). A User always sees their own Presence, and can still see Members who share theirs. Sharing is on by default.

### 4.1 Read the setting

`GET /api/v1/users/settings` returns `200 OK`:

```json
{ "sharePresence": true }
```

It returns `404 User not found` for an unknown caller.

### 4.2 Update the setting

`PUT /api/v1/users/settings` returns `200 OK` with the same body shape.

```json
{ "sharePresence": false }
```

`sharePresence` is required (`400 sharePresence is required`). After commit:

- **Turning sharing off** broadcasts an `OFFLINE` Presence update to colleagues immediately.
- **Turning sharing on** broadcasts `ONLINE` only if the User is currently connected.

Presence updates have the shape `{userId, username, status, timestamp}`. They go to `/user/queue/online` for each colleague (every User sharing a Workspace), to the User's own `/user/queue/online`, and to `/topic/workspace.{workspaceId}.presence` for each of the User's Workspaces.

### 4.3 Online colleagues

`GET /api/v1/users/online` returns `200 OK`. It lists the caller's colleagues (Users sharing at least one Workspace) who are connected **and** sharing Presence:

```json
[ { "status": "ONLINE", "userId": "42", "username": "bob" } ]
```

`userId` is a string in this response.

---

## 5. Static-resource not found

A request that matches no controller or static resource returns `404` with a fixed message. The `path` field is **omitted**, so the requested path is never echoed back:

```json
{ "timestamp": "2026-09-29T10:00:00Z", "status": 404, "error": "Not Found", "message": "Resource not found." }
```

Other responses from `GlobalExceptionHandler` still include `path`.
