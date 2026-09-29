import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import WorkspaceChannelViewport from "./WorkspaceChannelViewport";
import { useWorkspace, type ChannelDto } from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { apiFetch } from "@/lib/api";

jest.mock("@/context/WorkspaceContext");
jest.mock("@/context/AuthContext");
jest.mock("@/context/ConnectionContext");
jest.mock("@/lib/api");

// Mock react-virtuoso for testing
jest.mock("react-virtuoso", () => ({
  Virtuoso: ({ data, itemContent }: any) => (
    <div data-testid="virtuoso-container">
      {data.map((item: any, index: number) => (
        <div key={item.id || index}>{itemContent(index, item)}</div>
      ))}
    </div>
  ),
}));

describe("WorkspaceChannelViewport", () => {
  const mockPrivateChannel: ChannelDto = {
    id: 5,
    workspaceId: 1,
    conversationId: 50,
    name: "confidential",
    slug: "confidential",
    type: "PRIVATE",
    isArchived: false,
    isMember: true,
    memberCount: 3,
    createdAt: "2026-08-19T00:00:00Z",
    description: "Confidential channel",
  };

  const mockPublicChannel: ChannelDto = {
    id: 1,
    workspaceId: 1,
    conversationId: 10,
    name: "general",
    slug: "general",
    type: "PUBLIC",
    isArchived: false,
    isMember: true,
    memberCount: 10,
    createdAt: "2026-08-19T00:00:00Z",
  };

  const mockMessages = [
    {
      id: 1,
      content: "Hello team!",
      senderUserId: 101,
      senderUsername: "alice",
      senderFullName: "Alice Smith",
      sentAt: "2026-08-19T10:00:00Z",
      messageType: "CHAT",
    },
    {
      id: 2,
      content: "@alice added @bob and @carol to the channel",
      senderUserId: 101,
      senderUsername: "alice",
      sentAt: "2026-08-19T10:05:00Z",
      messageType: "SYSTEM",
    },
    {
      id: 3,
      content: "@bob left the channel",
      senderUserId: 102,
      senderUsername: "bob",
      sentAt: "2026-08-19T10:10:00Z",
      messageType: "SYSTEM",
    },
  ];

  beforeEach(() => {
    jest.clearAllMocks();

    (useAuth as jest.Mock).mockReturnValue({
      userId: 101,
      username: "alice",
    });

    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
      subscribe: jest.fn(() => jest.fn()),
      stompClientRef: { current: { publish: jest.fn() } },
    });

    (useWorkspace as jest.Mock).mockReturnValue({
      currentWorkspace: { id: 1, name: "Acme Corp", role: "OWNER" },
      members: [],
      presenceMap: {},
      getChannelMembers: jest.fn().mockResolvedValue([]),
      batchAddChannelMembers: jest.fn().mockResolvedValue({}),
      removeChannelMember: jest.fn().mockResolvedValue({}),
      leaveChannel: jest.fn().mockResolvedValue({}),
      createBatchInvites: jest.fn().mockResolvedValue({}),
      refreshChannels: jest.fn(),
    });

    (apiFetch as jest.Mock).mockResolvedValue({
      messages: mockMessages,
      nextCursor: null,
    });
  });

  it("renders header with channel name and members button", async () => {
    render(<WorkspaceChannelViewport channel={mockPrivateChannel} />);

    expect(screen.getByText("#confidential")).toBeInTheDocument();
    expect(screen.getByText("3 members")).toBeInTheDocument();
    expect(screen.getByText("Add Members")).toBeInTheDocument();
  });

  it("renders public channel header without Add Members shortcut", async () => {
    render(<WorkspaceChannelViewport channel={mockPublicChannel} />);

    expect(screen.getByText("#general")).toBeInTheDocument();
    expect(screen.getByText("10 members")).toBeInTheDocument();
    expect(screen.queryByText("Add Members")).not.toBeInTheDocument();
  });

  it("renders system messages as distinct centered activity notices", async () => {
    render(<WorkspaceChannelViewport channel={mockPrivateChannel} />);

    await waitFor(() => {
      expect(screen.getByText("Hello team!")).toBeInTheDocument();
      expect(
        screen.getByText("@alice added @bob and @carol to the channel")
      ).toBeInTheDocument();
      expect(screen.getByText("@bob left the channel")).toBeInTheDocument();
    });
  });

  it("opens ChannelDetailsModal when clicking member count button", async () => {
    render(<WorkspaceChannelViewport channel={mockPrivateChannel} />);

    const memberBtn = screen.getByTitle("View Channel Details & Members");
    fireEvent.click(memberBtn);

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText("Search channel members...")
      ).toBeInTheDocument();
    });
  });

  it("opens ChannelDetailsModal on Add Members tab when clicking Add Members button", async () => {
    render(<WorkspaceChannelViewport channel={mockPrivateChannel} />);

    const addBtn = screen.getByTitle("Add Members");
    fireEvent.click(addBtn);

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText("Search colleagues to add...")
      ).toBeInTheDocument();
    });
  });
});
