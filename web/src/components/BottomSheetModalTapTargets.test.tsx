import React from "react";
import { render, screen, fireEvent, within } from "@testing-library/react";
import CreateChannelModal from "./CreateChannelModal";
import WorkspaceOnboardingModal from "./WorkspaceOnboardingModal";
import InviteMembersModal from "./InviteMembersModal";
import MemberDirectoryModal from "./MemberDirectoryModal";
import ChannelDetailsModal from "./ChannelDetailsModal";
import {
  useWorkspace,
  type ChannelDto,
  type InvitationDto,
  type WorkspaceMemberDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useCall } from "@/context/CallContext";

jest.mock("@/context/WorkspaceContext");
jest.mock("@/context/AuthContext");
jest.mock("@/context/CallContext");

/**
 * ADR-0006: bottom-sheet modals give every action a 44x44px minimum tap
 * target. jsdom has no layout, so the Tailwind sizing classes stand in for
 * the rendered size.
 */
const meetsTapTarget = (el: Element) =>
  el.classList.contains("min-h-[44px]") &&
  el.classList.contains("min-w-[44px]");

/**
 * A checkbox or radio is tapped through its label row, so the row carries the
 * 44px height; its width already spans the sheet. A bare control must be
 * 44x44 on its own.
 */
const choiceMeetsTapTarget = (control: HTMLInputElement) => {
  const label = control.closest("label") ?? control.labels?.[0] ?? null;
  return label
    ? label.classList.contains("min-h-[44px]")
    : meetsTapTarget(control);
};

const describeAction = (el: Element) =>
  el.getAttribute("aria-label") ||
  el.getAttribute("title") ||
  (el.closest("label") ?? el).textContent?.trim();

function expectEveryActionToMeetTapTarget(root: HTMLElement = document.body) {
  const buttons = within(root).getAllByRole("button");
  expect(buttons.length).toBeGreaterThan(0);
  const choices = [
    ...within(root).queryAllByRole("checkbox"),
    ...within(root).queryAllByRole("radio"),
  ] as HTMLInputElement[];
  const undersized = [
    ...buttons.filter((b) => !meetsTapTarget(b)),
    ...choices.filter((c) => !choiceMeetsTapTarget(c)),
  ].map(describeAction);
  expect(undersized).toEqual([]);
}

const generalChannel: ChannelDto = {
  id: 10,
  workspaceId: 1,
  conversationId: 100,
  name: "general",
  slug: "general",
  type: "PUBLIC",
  isArchived: false,
  isMember: true,
  memberCount: 3,
  createdAt: "2026-08-19T00:00:00Z",
};

const joinLink: InvitationDto = {
  id: 50,
  workspaceId: 1,
  workspaceName: "Acme Corp",
  workspaceSlug: "acme-corp",
  token: "join-token",
  type: "SHAREABLE_LINK",
  role: "MEMBER",
  status: "PENDING",
  expiresAt: "2026-12-31T00:00:00Z",
  usesCount: 0,
};

const pendingInvite: InvitationDto = {
  ...joinLink,
  id: 51,
  token: "email-token",
  type: "EMAIL",
  email: "pending@example.com",
  inviterName: "alice",
};

const alice: WorkspaceMemberDto = {
  id: 1,
  workspaceId: 1,
  userId: 101,
  username: "alice",
  fullName: "Alice Smith",
  role: "OWNER",
  joinedAt: "2026-08-19T00:00:00Z",
};

const bob: WorkspaceMemberDto = {
  id: 2,
  workspaceId: 1,
  userId: 102,
  username: "bob",
  fullName: "Bob Jones",
  role: "MEMBER",
  joinedAt: "2026-08-19T00:00:00Z",
};

const carol: WorkspaceMemberDto = {
  id: 3,
  workspaceId: 1,
  userId: 103,
  username: "carol",
  fullName: "Carol Danvers",
  role: "MEMBER",
  joinedAt: "2026-08-19T00:00:00Z",
};

const privateChannel: ChannelDto = {
  ...generalChannel,
  id: 11,
  conversationId: 110,
  name: "secret-ops",
  slug: "secret-ops",
  type: "PRIVATE",
  memberCount: 2,
};

describe("Bottom-sheet modal tap targets (ADR-0006, #88)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useAuth as jest.Mock).mockReturnValue({ userId: 101, username: "alice" });
    (useCall as jest.Mock).mockReturnValue({ initiateCall: jest.fn() });
    (useWorkspace as jest.Mock).mockReturnValue({
      currentWorkspace: { id: 1, name: "Acme Corp", role: "OWNER" },
      channels: [generalChannel],
      members: [alice, bob, carol],
      presenceMap: { 101: "ONLINE", 102: "ONLINE" },
      getChannelMembers: jest.fn().mockResolvedValue([alice, bob]),
      batchAddChannelMembers: jest.fn().mockResolvedValue({
        added: [103],
        alreadyMembers: [],
        notWorkspaceMembers: [],
      }),
      removeChannelMember: jest.fn().mockResolvedValue(undefined),
      leaveChannel: jest.fn().mockResolvedValue(undefined),
      refreshChannels: jest.fn(),
      openDirectMessage: jest.fn(),
      getPendingInvitations: jest.fn().mockResolvedValue([pendingInvite]),
      resendInvitation: jest.fn().mockResolvedValue(undefined),
      revokeInvitation: jest.fn().mockResolvedValue(undefined),
      createChannel: jest.fn(),
      createBatchInvites: jest.fn(),
      getShareableJoinLink: jest.fn().mockResolvedValue(joinLink),
      toggleShareableJoinLink: jest.fn(),
      resetShareableJoinLink: jest.fn(),
    });
  });

  it("Create Channel: every action is at least 44px", () => {
    render(<CreateChannelModal isOpen={true} onClose={jest.fn()} />);

    expect(screen.getByText("Create a Channel")).toBeInTheDocument();
    expectEveryActionToMeetTapTarget();
  });

  it("Onboarding: every action is at least 44px", () => {
    render(<WorkspaceOnboardingModal isOpen={true} onClose={jest.fn()} />);

    expect(
      screen.getByRole("heading", { name: "Create Workspace" })
    ).toBeInTheDocument();
    expectEveryActionToMeetTapTarget();
  });

  it("Invite: every action on both tabs is at least 44px", async () => {
    render(<InviteMembersModal isOpen={true} onClose={jest.fn()} />);

    // Email tab, with a recipient chip whose remove action must be tappable too
    const emailInput = screen.getByPlaceholderText(
      "Enter emails separated by comma or space..."
    );
    fireEvent.change(emailInput, { target: { value: "new@example.com" } });
    fireEvent.keyDown(emailInput, { key: "Enter" });
    expect(screen.getByText("new@example.com")).toBeInTheDocument();
    expectEveryActionToMeetTapTarget();

    // Shareable link tab, including the reset confirmation
    fireEvent.click(
      screen.getByRole("button", { name: "Shareable Join Link" })
    );
    await screen.findByRole("button", { name: /Copy Link/ });
    expectEveryActionToMeetTapTarget();

    fireEvent.click(
      screen.getByRole("button", { name: /Reset Shareable Join Link/ })
    );
    expect(
      screen.getByRole("button", { name: "Confirm Reset" })
    ).toBeInTheDocument();
    expectEveryActionToMeetTapTarget();
  });

  it("Member Directory: every action on both tabs is at least 44px", async () => {
    render(
      <MemberDirectoryModal
        isOpen={true}
        onClose={jest.fn()}
        onOpenInvite={jest.fn()}
      />
    );

    // Members tab, with call and message actions for a colleague
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expectEveryActionToMeetTapTarget();

    // Pending Invitations tab, with resend/revoke and the toast's dismiss
    fireEvent.click(screen.getByRole("button", { name: /Pending/ }));
    fireEvent.click(await screen.findByTitle("Resend Invitation Email"));
    await screen.findByText(/Invitation re-sent to pending@example.com/);
    expectEveryActionToMeetTapTarget();
  });

  describe("Channel Details", () => {
    const renderChannelDetails = (
      initialTab: "members" | "add" | "email" = "members"
    ) =>
      render(
        <ChannelDetailsModal
          isOpen={true}
          onClose={jest.fn()}
          channel={privateChannel}
          initialTab={initialTab}
        />
      );

    it("Members tab: leave, remove, confirmations and feedback are at least 44px", async () => {
      renderChannelDetails("members");

      await screen.findByText("Bob Jones");
      expectEveryActionToMeetTapTarget();

      fireEvent.click(screen.getByRole("button", { name: /Leave/ }));
      expect(
        screen.getByRole("button", { name: "Confirm Leave" })
      ).toBeInTheDocument();
      expectEveryActionToMeetTapTarget();
      fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

      fireEvent.click(screen.getByTitle("Remove @bob from channel"));
      expect(
        screen.getByRole("button", { name: "Confirm Removal" })
      ).toBeInTheDocument();
      expectEveryActionToMeetTapTarget();

      fireEvent.click(screen.getByRole("button", { name: "Confirm Removal" }));
      await screen.findByText("@bob has been removed from #secret-ops.");
      expectEveryActionToMeetTapTarget();
    });

    it("Add tab: select-all, submit and the result's dismiss are at least 44px", async () => {
      renderChannelDetails("add");

      await screen.findByText("Carol Danvers");
      fireEvent.click(screen.getByRole("button", { name: "Select All" }));
      expectEveryActionToMeetTapTarget();

      fireEvent.click(
        screen.getByRole("button", { name: /Add to Channel \(1\)/ })
      );
      await screen.findByRole("status", { name: "Add members result" });
      expectEveryActionToMeetTapTarget();
    });

    it("Invite tab: chip removal, Close and Send are at least 44px", () => {
      renderChannelDetails("email");

      const emailInput = screen.getByPlaceholderText(
        "Enter emails separated by comma or space..."
      );
      fireEvent.change(emailInput, { target: { value: "new@example.com" } });
      fireEvent.keyDown(emailInput, { key: "Enter" });
      expect(screen.getByText("new@example.com")).toBeInTheDocument();
      expectEveryActionToMeetTapTarget();
    });
  });
});
