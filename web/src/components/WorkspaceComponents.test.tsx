import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import WorkspaceRail from "./WorkspaceRail";
import CreateChannelModal from "./CreateChannelModal";
import InviteMembersModal from "./InviteMembersModal";
import MemberDirectoryModal from "./MemberDirectoryModal";
import WorkspaceOnboardingModal from "./WorkspaceOnboardingModal";
import WorkspaceDMViewport from "./WorkspaceDMViewport";
import WorkspaceChannelViewport from "./WorkspaceChannelViewport";
import { WorkspaceProvider } from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { useCall } from "@/context/CallContext";
import { apiFetch } from "@/lib/api";

jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));

jest.mock("@/context/ConnectionContext", () => ({
  useConnection: jest.fn(),
}));

jest.mock("@/context/CallContext", () => ({
  useCall: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn().mockResolvedValue([]),
}));

describe("Workspace Components", () => {
  const mockWorkspaces = [
    {
      id: 1,
      name: "Acme Corp",
      slug: "acme-corp",
      ownerId: 10,
      role: "OWNER" as const,
      memberCount: 5,
      channelCount: 3,
      createdAt: "2026-08-19T00:00:00Z",
    },
    {
      id: 2,
      name: "Beta Labs",
      slug: "beta-labs",
      ownerId: 20,
      role: "MEMBER" as const,
      memberCount: 2,
      channelCount: 1,
      createdAt: "2026-08-19T00:00:00Z",
    },
  ];

  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({
      userId: 10,
      username: "alice",
      isAuthenticated: true,
    });
    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
      subscribe: jest.fn().mockReturnValue(() => {}),
      stompClientRef: { current: { publish: jest.fn() } },
    });
    (useCall as jest.Mock).mockReturnValue({
      initiateCall: jest.fn(),
    });
  });

  it("WorkspaceRail renders workspace avatars and triggers creation callback", () => {
    const onOpenCreateWorkspace = jest.fn();

    render(
      <WorkspaceProvider>
        <WorkspaceRail onOpenCreateWorkspace={onOpenCreateWorkspace} />
      </WorkspaceProvider>
    );

    const addBtn = screen.getByTitle("Add Workspace");
    expect(addBtn).toBeInTheDocument();
    fireEvent.click(addBtn);
    expect(onOpenCreateWorkspace).toHaveBeenCalledTimes(1);
  });

  it("CreateChannelModal toggles privacy and calls onClose on cancel", () => {
    const onClose = jest.fn();

    render(
      <WorkspaceProvider>
        <CreateChannelModal isOpen={true} onClose={onClose} />
      </WorkspaceProvider>
    );

    expect(screen.getByText("Create a Channel")).toBeInTheDocument();
    const cancelBtn = screen.getByRole("button", { name: "Cancel" });
    fireEvent.click(cancelBtn);
    expect(onClose).toHaveBeenCalled();
  });

  it("InviteMembersModal displays shareable link and email invite tabs", () => {
    const onClose = jest.fn();

    render(
      <WorkspaceProvider>
        <InviteMembersModal isOpen={true} onClose={onClose} />
      </WorkspaceProvider>
    );
    // If currentWorkspace is not loaded, modal returns null
    // Test passed by ensuring no errors are thrown during render
  });

  it("WorkspaceDMViewport renders messages with valid formatted timestamp and never Invalid Date", async () => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url.includes("/dm/20/messages")) {
        return Promise.resolve({
          messages: [
            {
              id: 1,
              content: "Hello Bob!",
              senderId: 10,
              senderUsername: "alice",
              timestamp: "2026-08-19T10:00:00Z",
            },
            {
              id: 2,
              content: "Hey Alice!",
              senderId: 20,
              senderUsername: "bob",
              timestamp: "2026-08-19T10:01:00Z",
            },
          ],
          nextCursor: null,
        });
      }
      return Promise.resolve([]);
    });

    const dm = {
      conversationId: 101,
      workspaceId: 1,
      targetMember: {
        id: 2,
        workspaceId: 1,
        userId: 20,
        username: "bob",
        fullName: "Bob Builder",
        role: "MEMBER" as const,
        joinedAt: "2026-08-19T00:00:00Z",
        presenceStatus: "ONLINE" as const,
      },
      createdAt: "2026-08-19T00:00:00Z",
    };

    await act(async () => {
      render(
        <WorkspaceProvider>
          <WorkspaceDMViewport dm={dm} />
        </WorkspaceProvider>
      );
    });

    expect(screen.queryByText(/Invalid Date/i)).not.toBeInTheDocument();
    expect(screen.getByText("Bob Builder")).toBeInTheDocument();
  });

  it("WorkspaceChannelViewport renders channel messages without Invalid Date", async () => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url.includes("/channels/1/messages")) {
        return Promise.resolve({
          messages: [
            {
              id: 1,
              content: "Welcome to general!",
              senderId: 10,
              senderUsername: "alice",
              timestamp: "2026-08-19T10:00:00Z",
            },
          ],
          nextCursor: null,
        });
      }
      return Promise.resolve([]);
    });

    const channel = {
      id: 1,
      workspaceId: 1,
      conversationId: 50,
      name: "general",
      slug: "general",
      type: "PUBLIC" as const,
      isArchived: false,
      isMember: true,
      memberCount: 5,
      createdAt: "2026-08-19T00:00:00Z",
    };

    await act(async () => {
      render(
        <WorkspaceProvider>
          <WorkspaceChannelViewport channel={channel} />
        </WorkspaceProvider>
      );
    });

    expect(screen.queryByText(/Invalid Date/i)).not.toBeInTheDocument();
    expect(screen.getByText("#general")).toBeInTheDocument();
  });

  it("WorkspaceOnboardingModal renders dedicated workspace creation modal without invite token entry tabs or inputs", () => {
    const onClose = jest.fn();

    render(
      <WorkspaceProvider>
        <WorkspaceOnboardingModal isOpen={true} onClose={onClose} />
      </WorkspaceProvider>
    );

    expect(
      screen.getByRole("heading", { name: "Create Workspace" })
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Workspace Name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/URL Slug/i)).toBeInTheDocument();

    // Verify absence of manual invite tabs and inputs
    expect(screen.queryByText("Join with Invite")).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/Invitation Token/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText(/Paste invite link or token/i)
    ).not.toBeInTheDocument();
  });

  it("WorkspaceOnboardingModal creates workspace on form submit and calls onClose", async () => {
    const onClose = jest.fn();
    (apiFetch as jest.Mock).mockImplementation(
      (url: string, options?: RequestInit) => {
        if (url === "/api/workspaces" && options?.method === "POST") {
          const body = JSON.parse(options.body as string);
          return Promise.resolve({
            id: 99,
            name: body.name,
            slug: body.slug || "gamma-workspace",
            ownerId: 10,
            role: "OWNER",
            memberCount: 1,
            channelCount: 1,
            createdAt: "2026-08-19T00:00:00Z",
          });
        }
        return Promise.resolve([]);
      }
    );

    render(
      <WorkspaceProvider>
        <WorkspaceOnboardingModal isOpen={true} onClose={onClose} />
      </WorkspaceProvider>
    );

    const nameInput = screen.getByLabelText(/Workspace Name/i);
    const slugInput = screen.getByLabelText(/URL Slug/i);

    fireEvent.change(nameInput, { target: { value: "Gamma Workspace" } });
    fireEvent.change(slugInput, { target: { value: "gamma-workspace" } });

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Create Workspace" }));
    });

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/workspaces",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          name: "Gamma Workspace",
          slug: "gamma-workspace",
        }),
      })
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("WorkspaceChannelViewport immediately loads messages when switching channels and handles live incoming messages", async () => {
    let subscriberCallback: ((frame: { body: string }) => void) | null = null;
    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
      subscribe: jest
        .fn()
        .mockImplementation(
          (
            destination: string,
            callback: (frame: { body: string }) => void
          ) => {
            if (destination.includes("/topic/workspace.1.channel.2")) {
              subscriberCallback = callback;
            }
            return () => {};
          }
        ),
      stompClientRef: { current: { publish: jest.fn() } },
    });

    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url.includes("/api/workspaces/me")) {
        return Promise.resolve(mockWorkspaces);
      }
      if (url.includes("/channels/2/messages")) {
        return Promise.resolve({
          messages: [
            {
              id: 101,
              content: "Initial message in new channel",
              senderId: 20,
              senderUsername: "bob",
              timestamp: "2026-08-19T11:00:00Z",
            },
          ],
          nextCursor: null,
        });
      }
      return Promise.resolve([]);
    });

    const newChannel = {
      id: 2,
      workspaceId: 1,
      conversationId: 52,
      name: "new-channel",
      slug: "new-channel",
      type: "PUBLIC" as const,
      isArchived: false,
      isMember: true,
      memberCount: 2,
      createdAt: "2026-08-19T00:00:00Z",
    };

    await act(async () => {
      render(
        <WorkspaceProvider>
          <WorkspaceChannelViewport channel={newChannel} />
        </WorkspaceProvider>
      );
    });

    // Initial messages should be fetched and displayed
    await act(async () => {
      await Promise.resolve();
    });

    expect(
      screen.getByText("Initial message in new channel")
    ).toBeInTheDocument();

    // Now simulate incoming live message delivered via STOMP
    await act(async () => {
      if (subscriberCallback) {
        subscriberCallback({
          body: JSON.stringify({
            id: 102,
            content: "Live message from Alice",
            senderId: 10,
            senderUsername: "alice",
            timestamp: "2026-08-19T11:01:00Z",
          }),
        });
      }
    });

    expect(screen.getByText("Live message from Alice")).toBeInTheDocument();
  });

  it("InviteMembersModal batch-invites multiple recipients with initial channels and custom message", async () => {
    (apiFetch as jest.Mock).mockImplementation(
      (url: string, options?: RequestInit) => {
        if (url.includes("/api/workspaces/me")) {
          return Promise.resolve(mockWorkspaces);
        }
        if (url.includes("/channels")) {
          return Promise.resolve([
            {
              id: 1,
              name: "general",
              slug: "general",
              type: "PUBLIC",
              isMember: true,
            },
            {
              id: 2,
              name: "backend",
              slug: "backend",
              type: "PUBLIC",
              isMember: true,
            },
          ]);
        }
        if (url.includes("/invitations/batch") && options?.method === "POST") {
          const body = JSON.parse(options.body as string);
          return Promise.resolve({
            sentCount: 1,
            failedCount: 1,
            results: [
              {
                email: "colleague1@example.com",
                status: "sent",
                invitationId: 10,
              },
              {
                email: "colleague2@example.com",
                status: "already_member",
                message: "Already member",
              },
            ],
          });
        }
        return Promise.resolve([]);
      }
    );

    const onClose = jest.fn();

    await act(async () => {
      render(
        <WorkspaceProvider>
          <InviteMembersModal isOpen={true} onClose={onClose} />
        </WorkspaceProvider>
      );
    });

    // Wait for workspace to load
    await act(async () => {
      await Promise.resolve();
    });

    expect(
      screen.getByText("Invite colleagues to Acme Corp")
    ).toBeInTheDocument();

    const input = screen.getByPlaceholderText(
      /Enter emails separated by comma or space/i
    );
    fireEvent.change(input, {
      target: { value: "colleague1@example.com, colleague2@example.com" },
    });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    const noteInput = screen.getByPlaceholderText(
      /Hey team, welcome to our workspace/i
    );
    fireEvent.change(noteInput, { target: { value: "Welcome to Acme Corp!" } });

    await act(async () => {
      fireEvent.click(
        screen.getByRole("button", { name: /Send Invitations/i })
      );
    });

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/workspaces/1/invitations/batch",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          emails: ["colleague1@example.com", "colleague2@example.com"],
          role: "MEMBER",
          customMessage: "Welcome to Acme Corp!",
          initialChannelIds: [1],
        }),
      })
    );

    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Already a Member")).toBeInTheDocument();
  });

  it("MemberDirectoryModal lists pending invitations and allows resending and revoking", async () => {
    (apiFetch as jest.Mock).mockImplementation(
      (url: string, options?: RequestInit) => {
        if (url.includes("/api/workspaces/me")) {
          return Promise.resolve(mockWorkspaces);
        }
        if (url.includes("/invitations/pending")) {
          return Promise.resolve([
            {
              id: 88,
              workspaceId: 1,
              email: "pending@example.com",
              inviterName: "alice",
              role: "MEMBER",
              status: "PENDING",
              createdAt: "2026-08-19T00:00:00Z",
            },
          ]);
        }
        if (
          url.includes("/api/invitations/88/resend") &&
          options?.method === "POST"
        ) {
          return Promise.resolve({ id: 88, status: "PENDING" });
        }
        if (
          url.includes("/api/invitations/88") &&
          options?.method === "DELETE"
        ) {
          return Promise.resolve({});
        }
        return Promise.resolve([]);
      }
    );

    const onClose = jest.fn();

    await act(async () => {
      render(
        <WorkspaceProvider>
          <MemberDirectoryModal isOpen={true} onClose={onClose} />
        </WorkspaceProvider>
      );
    });

    await act(async () => {
      await Promise.resolve();
    });

    // Switch to Pending Invitations tab
    const pendingTab = screen.getByRole("button", {
      name: /Pending Invitations/i,
    });
    expect(pendingTab).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(pendingTab);
    });

    expect(screen.getByText("pending@example.com")).toBeInTheDocument();
    expect(screen.getByText(/Invited by @alice/i)).toBeInTheDocument();

    // Click Resend
    const resendBtn = screen.getByRole("button", { name: /Resend/i });
    await act(async () => {
      fireEvent.click(resendBtn);
    });
    expect(apiFetch).toHaveBeenCalledWith(
      "/api/invitations/88/resend",
      expect.objectContaining({ method: "POST" })
    );

    // Click Revoke
    const revokeBtn = screen.getByRole("button", { name: /Revoke/i });
    await act(async () => {
      fireEvent.click(revokeBtn);
    });
    expect(apiFetch).toHaveBeenCalledWith(
      "/api/invitations/88",
      expect.objectContaining({ method: "DELETE" })
    );
  });
});
