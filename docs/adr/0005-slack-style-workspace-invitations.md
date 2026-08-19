# Slack-style Workspace Invitations

Workspace invitations were previously restricted to individual email dispatches by Owners/Admins without initial channel assignment or invite management. We decided to transition the invitation workflow to match Slack's model: supporting tokenized multi-email batch entry with granular status reporting, inviter-selectable initial channels auto-joined upon acceptance, open member-inviting permissions (with administrative roles and revocation reserved for Admins/Owners), pending invitation tracking with resend/revoke controls, optional custom welcome notes, and a persistent, revocable workspace shareable join link.

## Considered Options

- **Single email dispatch only** — simpler form UI, but rejected because team onboarding requires tedious repetitive submissions rather than copy-pasting full recipient lists.
- **Strict Admin-only inviting** — higher barrier against unwanted invites, but slows down workspace growth. Adopted Slack's default where any Member can invite others as `MEMBER`, while role escalation to `ADMIN` and invite revocation require Admin/Owner privileges.
- **Default `#general` channel only on join** — reduces invitation payload size, but forces workspace admins to manually add members to project channels after onboarding. Adopted pre-assigned initial channels (all public channels plus inviter's private channels) stored on the invitation and auto-joined on acceptance.
- **Ad-hoc 7-day expiring join links** — forces recurring regeneration of team links. Adopted a canonical persistent workspace shareable join link that Admins can toggle on/off or reset on demand.
- **All-or-nothing batch transactions** — cleaner rollback semantics, but poor UX when 1 out of 10 emails is already registered. Adopted per-email granular status results (`sent`, `already_member`, `already_invited`, `invalid_email`).

## Consequences

- Backend `POST /api/workspaces/{id}/invitations/batch` endpoint added to accept email lists, custom notes, and initial channel IDs.
- `WorkspaceInvitation` entity extended with `customMessage` and `initialChannelIds` (JSON / comma-separated relation).
- Member Directory updated to include a "Pending Invitations" tab for Admins and Owners with Resend and Revoke endpoints (`POST /api/invitations/{id}/resend`, `DELETE /api/invitations/{id}`).
- Frontend `InviteMembersModal` upgraded to a multi-email chip input with channel picker and personalized note textarea.
- Persistent workspace shareable join link stored per workspace with reset capability.
