---
status: accepted
---

# Async Presence Persistence

We persist a user's "last seen" Presence timestamp to Redis when their WebSocket disconnects, and use a periodic background job to sync these timestamps to the main database. 

We rejected writing directly to the main database on every disconnect. In a chat application, users can drop connection frequently (e.g., locking their phone, entering a tunnel), which would cause a high volume of writes to the primary database simply to update a timestamp. By buffering these updates in Redis and syncing periodically, we significantly reduce the load on the database at the cost of slight eventual consistency for the "last seen" display.

## Addendum (2026-09-29): Presence Sharing cache (#89)

To keep connect and disconnect light, each User's Presence Sharing choice is cached in Redis alongside the Presence keys (`PresenceModuleImpl`):

- **Key:** `user:presence:sharing:{userId}`. The value is a boolean with a 1-hour TTL.
- **Fill:** on a cache miss, the stored choice is read from the database (sharing is on when no settings row exists) and written with `setIfAbsent`. A read that races a change therefore can't overwrite the newer value. Multi-User checks (member lists, the online-user list) fetch the keys in one `multiGet`, then run one batched database lookup for the misses.
- **Refresh:** `setPresenceSharing` writes the new value through **after commit**, together with the resulting Presence broadcast. If that write fails, the key is deleted so the next read falls back to the database.
- **Redis down:** reads fall back to the database choice. Failed cache writes are logged and ignored.
- **Namespace:** the key sits under `user:presence:`, so both SCAN-based jobs (`getPresenceMap` and `scheduledSyncAllPresenceViaScan`) skip it via `isLastSeenKey`, along with `user:presence:offline_sync`. Any new key under this prefix must be excluded the same way.
- **Remaining cost:** once the choice is cached, a sharing Member's connect or disconnect still makes 2 database reads that are not settings reads: the eligible Presence receivers and the Member's Workspace memberships. A Member who is not sharing makes neither.
