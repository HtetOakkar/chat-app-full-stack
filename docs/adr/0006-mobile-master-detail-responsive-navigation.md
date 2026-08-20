# Mobile Master-Detail Responsive Navigation

On desktop viewports (>= 768px), the application renders a 3-tier multi-tenant layout: Tier 1 (Workspace Rail, 64px), Tier 2 (Workspace Sidebar, 240px–288px), and Tier 3 (Main Content Viewport). On mobile viewports (< 768px), rendering all columns simultaneously creates severe horizontal clipping and leaves inadequate space for conversations and virtual keyboard input. We decided to implement a Master-Detail stack navigation model on mobile devices: hiding the fixed rail, displaying the Workspace Sidebar as the root view, opening active Channel and Direct Message conversations as full-width views with a prominent `< Back` button, and providing workspace switching via a top sheet/dropdown.

## Considered Options

- **Permanent Left Mini-Rail on Mobile** — kept the 64px Workspace Rail pinned to the left edge on mobile, collapsing only the sidebar. Rejected because it permanently consumes ~20% of narrow phone screens (~360px-400px width) for an action users perform infrequently.
- **Slide-over Off-Canvas Drawer** — rendered the conversation viewport as the persistent base layer and used a hamburger menu to slide over the channel list. Rejected because users navigating a chat app need to see unread badges and active channels at a glance upon landing, rather than being forced to open a drawer on every session.
- **Master-Detail Stack Flow (Adopted)** — mobile screens render one primary panel at a time. The root view is the Workspace Sidebar (Channels and Direct Messages). Tapping a conversation transitions to the full-width Conversation Viewport with a top back button returning to the Sidebar. Workspace switching is handled via a top workspace header dropdown / bottom sheet modal.
- **Fixed Bottom Navigation Bar** — added a fixed bottom bar switching between tabs. Rejected because Channels and Direct Messages already form a cohesive hierarchical workspace structure, and bottom bars conflict with virtual keyboard resizing on iOS/Android.

## Consequences

- On screens `< 768px` (`md`), the Workspace Rail is hidden from the main view and workspace switching is integrated into the Workspace Sidebar header.
- When an active Channel or Direct Message is selected on mobile, the Conversation Viewport fills the full screen width and displays a top back button (`←`) in the header that resets active chat state and returns to the Sidebar.
- Modals, profile cards, and settings views adapt to full-screen or bottom-anchored sheets on mobile screens with touch-friendly 44x44px minimum tap targets.
- Dynamic viewport units (`100dvh`) and flex layout are used across chat viewports to seamlessly adapt to virtual keyboard opening.
