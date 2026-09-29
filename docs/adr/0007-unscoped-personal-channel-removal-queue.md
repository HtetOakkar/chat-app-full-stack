---
status: accepted
---

# Unscoped Personal Queue for Removal Notices

When an Owner or Admin removes a Member from a Private Channel, only the removed Member learns who removed them (#82, #96). We deliver that Removal Notice on `/user/queue/channel-removals`, a personal queue that is deliberately **not** Workspace-scoped. That breaks the convention that user queues are named `/user/queue/workspace.{workspaceId}.*` and are authorized by `WorkspaceSecurityChannelInterceptor` (see `docs/research/0001-workspace-stomp-websocket-routing-and-security.md`). We accepted this because the removed Member must be told wherever they are in the app, including while they have a different Workspace open (#89, story 14). The web client subscribes to Workspace-scoped queues only for the current Workspace, so a Workspace-scoped queue would miss them.

## Considered Options

- **Workspace-scoped queue** (`/user/queue/workspace.{workspaceId}.channel-removals`): this follows the convention and is authorized by the existing interceptor. It was rejected because the client would need to subscribe for every Workspace the User belongs to, and keep those subscriptions in step as Workspaces are joined and left, or else miss notices sent while another Workspace is open.
- **Persist the notice** (a stored, per-User notification read on next load): this would also reach Members who are offline at removal time. It was rejected as more than the review called for. It needs a new table, read state and an API. Recording removals as an audit trail is out of scope for #89.
- **Unscoped personal queue (adopted):** one subscription per connection, made once after connecting and independent of the current Workspace.

## Consequences

- `WorkspaceSecurityChannelInterceptor` performs no Workspace check on this queue, because its path has no Workspace ID. Isolation comes from Spring's user-destination resolution instead: a subscription to `/user/queue/channel-removals` resolves to the subscriber's own session queue, and the server publishes only to the removed User's ID. The payload carries `workspaceId`, so the client can tell which Workspace the removal belongs to.
- Delivery is live only. A removed Member who is offline at removal time is never told who removed them; they just find the Channel gone. This is accepted behaviour.
- The notice is published after the removal commits, so a rolled-back removal sends nothing.
- Any future personal, cross-Workspace notice should reuse this pattern and be recorded as an exception. It should not quietly widen the unscoped namespace.
