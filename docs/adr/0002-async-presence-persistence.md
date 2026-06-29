---
status: accepted
---

# Async Presence Persistence

We persist a user's "last seen" Presence timestamp to Redis when their WebSocket disconnects, and use a periodic background job to sync these timestamps to the main database. 

We rejected writing directly to the main database on every disconnect. In a chat application, users can drop connection frequently (e.g., locking their phone, entering a tunnel), which would cause a high volume of writes to the primary database simply to update a timestamp. By buffering these updates in Redis and syncing periodically, we significantly reduce the load on the database at the cost of slight eventual consistency for the "last seen" display.
