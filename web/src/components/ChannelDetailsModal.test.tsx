import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import ChannelDetailsModal from "./ChannelDetailsModal";
import {
  useWorkspace,
  type ChannelDto,
  type WorkspaceMemberDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";

jest.mock("@/context/WorkspaceContext");
jest.mock("@/context/AuthContext");

describe("ChannelDetailsModal", () => {
  const mockChannel: ChannelDto = {
    id: 10,
    workspaceId: 1,
    conversationId: 100,
    name: "secret-ops",
    slug: "secret-ops",
    type: "PRIVATE",
    isArchived: false,
    isMember: true,
    memberCount: 2,
    createdAt: "2026-08-19T00:00:00Z",
    description: "Private discussions",
  };

  const mockChannelMembers: WorkspaceMemberDto[] = [
    {
      id: 1,
      workspaceId: 1,
      userId: 101,
      username: "alice",
      fullName: "Alice Smith",
      role: "OWNER",
      joinedAt: "2026-08-19T00:00:00Z",
    },
    {
      id: 2,
      workspaceId: 1,
      userId: 102,
      username: "bob",
      fullName: "Bob Jones",
      role: "MEMBER",
      joinedAt: "2026-08-19T00:00:00Z",
    },
  ];

  const mockAllWorkspaceMembers: WorkspaceMemberDto[] = [
    ...mockChannelMembers,
    {
      id: 3,
      workspaceId: 1,
      userId: 103,
      username: "carol",
      fullName: "Carol Danvers",
      role: "MEMBER",
      joinedAt: "2026-08-19T00:00:00Z",
    },
    {
      id: 4,
      workspaceId: 1,
      userId: 104,
      username: "david",
      fullName: "David Clark",
      role: "MEMBER",
      joinedAt: "2026-08-19T00:00:00Z",
    },
  ];

  const mockGetChannelMembers = jest.fn();
  const mockBatchAddChannelMembers = jest.fn();
  const mockRemoveChannelMember = jest.fn();
  const mockLeaveChannel = jest.fn();
  const mockCreateBatchInvites = jest.fn();
  const mockRefreshChannels = jest.fn();
  const mockOnClose = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    (useAuth as jest.Mock).mockReturnValue({
      userId: 101, // Alice (Owner)
      username: "alice",
    });

    mockGetChannelMembers.mockResolvedValue(mockChannelMembers);
    mockBatchAddChannelMembers.mockResolvedValue({
      added: [103, 104],
      alreadyMembers: [],
      notWorkspaceMembers: [],
    });
    mockRemoveChannelMember.mockResolvedValue({});
    mockLeaveChannel.mockResolvedValue({});
    mockCreateBatchInvites.mockResolvedValue({
      sentCount: 1,
      failedCount: 0,
      results: [{ email: "newuser@example.com", status: "sent" }],
    });

    (useWorkspace as jest.Mock).mockReturnValue({
      currentWorkspace: { id: 1, name: "Acme Corp", role: "OWNER" },
      members: mockAllWorkspaceMembers,
      presenceMap: { 101: "ONLINE", 102: "OFFLINE", 103: "ONLINE" },
      getChannelMembers: mockGetChannelMembers,
      batchAddChannelMembers: mockBatchAddChannelMembers,
      removeChannelMember: mockRemoveChannelMember,
      leaveChannel: mockLeaveChannel,
      createBatchInvites: mockCreateBatchInvites,
      refreshChannels: mockRefreshChannels,
    });
  });

  it("renders channel name, description, and member list in Members tab", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="members"
      />
    );

    expect(screen.getByText("#secret-ops")).toBeInTheDocument();
    expect(screen.getByText("Private discussions")).toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetChannelMembers).toHaveBeenCalledWith(10);
    });

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getByText(/@alice/)).toBeInTheDocument();
    expect(screen.getByText(/@bob/)).toBeInTheDocument();
  });

  it("allows search filtering of channel members", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="members"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(
      "Search channel members..."
    );
    fireEvent.change(searchInput, { target: { value: "bob" } });

    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
  });

  it("keeps a separate search per tab, so a query typed on one tab doesn't filter another", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="members"
      />
    );
    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    // Members tab: search for bob
    fireEvent.change(screen.getByPlaceholderText("Search channel members..."), {
      target: { value: "bob" },
    });
    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();

    // Add tab starts with its own, empty search
    fireEvent.click(screen.getByRole("button", { name: /^Add Members/ }));
    const addSearch = screen.getByPlaceholderText(
      "Search colleagues to add..."
    );
    expect(addSearch).toHaveValue("");
    expect(screen.getByText("Carol Danvers")).toBeInTheDocument();
    expect(screen.getByText("David Clark")).toBeInTheDocument();

    fireEvent.change(addSearch, { target: { value: "carol" } });
    expect(screen.queryByText("David Clark")).not.toBeInTheDocument();

    // Invite tab doesn't inherit either query
    fireEvent.click(screen.getByRole("button", { name: "Invite by Email" }));
    expect(
      screen.getByPlaceholderText("Enter emails separated by comma or space...")
    ).toHaveValue("");

    // Members tab still has its own query
    fireEvent.click(screen.getByRole("button", { name: /^Members/ }));
    expect(
      screen.getByPlaceholderText("Search channel members...")
    ).toHaveValue("bob");
    expect(screen.queryByText("Alice Smith")).not.toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();

    // ...and so does the Add tab
    fireEvent.click(screen.getByRole("button", { name: /^Add Members/ }));
    expect(
      screen.getByPlaceholderText("Search colleagues to add...")
    ).toHaveValue("carol");
    expect(screen.getByText("Carol Danvers")).toBeInTheDocument();
    expect(screen.queryByText("David Clark")).not.toBeInTheDocument();
  });

  it("allows an admin/owner to remove another member with confirmation", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="members"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    });

    const removeBtn = screen.getByTitle("Remove @bob from channel");
    fireEvent.click(removeBtn);

    expect(
      screen.getByText("Remove @bob from #secret-ops?")
    ).toBeInTheDocument();

    const confirmBtn = screen.getByText("Confirm Removal");
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockRemoveChannelMember).toHaveBeenCalledWith(10, 102);
    });
  });

  it("allows a member to leave the channel with confirmation", async () => {
    (useAuth as jest.Mock).mockReturnValue({
      userId: 102, // Bob
      username: "bob",
    });
    (useWorkspace as jest.Mock).mockReturnValue({
      currentWorkspace: { id: 1, name: "Acme Corp", role: "MEMBER" },
      members: mockAllWorkspaceMembers,
      presenceMap: {},
      getChannelMembers: mockGetChannelMembers,
      batchAddChannelMembers: mockBatchAddChannelMembers,
      removeChannelMember: mockRemoveChannelMember,
      leaveChannel: mockLeaveChannel,
      createBatchInvites: mockCreateBatchInvites,
      refreshChannels: mockRefreshChannels,
    });

    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="members"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Leave")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Leave"));
    expect(screen.getByText("Leave #secret-ops?")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Confirm Leave"));

    await waitFor(() => {
      expect(mockLeaveChannel).toHaveBeenCalledWith(10);
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  it("supports multi-selection and batch adding members in Add Members tab", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="add"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Carol Danvers")).toBeInTheDocument();
      expect(screen.getByText("David Clark")).toBeInTheDocument();
    });

    // Select All
    fireEvent.click(screen.getByText("Select All"));
    expect(screen.getByText("2 of 2 selected")).toBeInTheDocument();

    const submitBtn = screen.getByRole("button", {
      name: /Add to Channel \(2\)/i,
    });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockBatchAddChannelMembers).toHaveBeenCalledWith(10, [103, 104]);
    });
  });

  describe("Add Members result", () => {
    const renderAddTab = () =>
      render(
        <ChannelDetailsModal
          isOpen={true}
          onClose={mockOnClose}
          channel={mockChannel}
          initialTab="add"
        />
      );

    /** The always-mounted result region, once the result has filled it. */
    const findAdditionResult = async () => {
      const region = screen.getByRole("status", {
        name: "Add members result",
      });
      await waitFor(() => expect(region).not.toBeEmptyDOMElement());
      return region;
    };

    const submitAll = async () => {
      await waitFor(() => {
        expect(screen.getByText("Carol Danvers")).toBeInTheDocument();
      });
      fireEvent.click(screen.getByText("Select All"));
      fireEvent.click(
        screen.getByRole("button", { name: /Add to Channel \(2\)/i })
      );
    };

    it("announces the result in a live region that was already mounted before I submitted", async () => {
      mockBatchAddChannelMembers.mockResolvedValue({
        added: [103, 104],
        alreadyMembers: [],
        notWorkspaceMembers: [],
      });
      renderAddTab();

      // Screen readers skip live regions inserted already filled, so the
      // region must be waiting, empty, before the result comes in.
      const region = screen.getByRole("status", {
        name: "Add members result",
      });
      expect(region).toBeEmptyDOMElement();

      await submitAll();

      await waitFor(() =>
        expect(region).toHaveTextContent("Added 2 colleagues to #secret-ops.")
      );
      expect(screen.getByRole("status", { name: "Add members result" })).toBe(
        region
      );

      fireEvent.click(
        within(region).getByRole("button", {
          name: "Dismiss add members result",
        })
      );
      expect(screen.getByRole("status", { name: "Add members result" })).toBe(
        region
      );
      expect(region).toBeEmptyDOMElement();
    });

    it("shows who was added, who was already a Channel Member and who isn't in the Workspace", async () => {
      mockBatchAddChannelMembers.mockResolvedValue({
        added: [103],
        alreadyMembers: [104],
        notWorkspaceMembers: [999],
      });
      renderAddTab();

      await submitAll();

      const result = await findAdditionResult();
      expect(
        within(within(result).getByRole("list", { name: "Added" })).getByText(
          "@carol"
        )
      ).toBeInTheDocument();
      expect(
        within(
          within(result).getByRole("list", { name: "Already in the channel" })
        ).getByText("@david")
      ).toBeInTheDocument();
      expect(
        within(
          within(result).getByRole("list", { name: "Not in this Workspace" })
        ).getByText("User #999")
      ).toBeInTheDocument();
      expect(mockGetChannelMembers).toHaveBeenCalledTimes(2);
    });

    it("says clearly when nobody was added", async () => {
      mockBatchAddChannelMembers.mockResolvedValue({
        added: [],
        alreadyMembers: [103, 104],
        notWorkspaceMembers: [],
      });
      renderAddTab();

      await submitAll();

      const result = await findAdditionResult();
      expect(within(result).getByText("No one was added.")).toBeInTheDocument();
      expect(
        within(result).queryByRole("list", { name: "Added" })
      ).not.toBeInTheDocument();
      expect(
        within(result).queryByRole("list", { name: "Not in this Workspace" })
      ).not.toBeInTheDocument();
    });

    it("uses the same batch addition and result for a single selection", async () => {
      mockBatchAddChannelMembers.mockResolvedValue({
        added: [],
        alreadyMembers: [],
        notWorkspaceMembers: [103],
      });
      renderAddTab();

      await waitFor(() => {
        expect(screen.getByText("Carol Danvers")).toBeInTheDocument();
      });
      fireEvent.click(screen.getByText("Carol Danvers"));
      fireEvent.click(
        screen.getByRole("button", { name: /Add to Channel \(1\)/i })
      );

      const result = await findAdditionResult();
      expect(mockBatchAddChannelMembers).toHaveBeenCalledWith(10, [103]);
      expect(
        within(
          within(result).getByRole("list", { name: "Not in this Workspace" })
        ).getByText("@carol")
      ).toBeInTheDocument();
    });

    it("blocks submission and explains the limit when more than 50 colleagues are selected", async () => {
      const manyCandidates: WorkspaceMemberDto[] = Array.from(
        { length: 51 },
        (_, i) => ({
          id: 1000 + i,
          workspaceId: 1,
          userId: 2000 + i,
          username: `colleague${i}`,
          fullName: `Colleague ${i}`,
          role: "MEMBER",
          joinedAt: "2026-08-19T00:00:00Z",
        })
      );
      (useWorkspace as jest.Mock).mockReturnValue({
        ...(useWorkspace as jest.Mock)(),
        members: [...mockChannelMembers, ...manyCandidates],
      });
      renderAddTab();

      await waitFor(() => {
        expect(screen.getByText("Colleague 50")).toBeInTheDocument();
      });
      fireEvent.click(screen.getByText("Select All"));

      expect(screen.getByRole("alert")).toHaveTextContent(
        "You can add at most 50 colleagues at a time"
      );
      const submitBtn = screen.getByRole("button", {
        name: /Add to Channel \(51\)/i,
      });
      expect(submitBtn).toBeDisabled();
      fireEvent.click(submitBtn);
      expect(mockBatchAddChannelMembers).not.toHaveBeenCalled();
    });
  });

  it("supports in-channel email invitations shortcut with pre-assigned channel", async () => {
    render(
      <ChannelDetailsModal
        isOpen={true}
        onClose={mockOnClose}
        channel={mockChannel}
        initialTab="email"
      />
    );

    expect(screen.getAllByText("#secret-ops")[0]).toBeInTheDocument();
    expect(screen.getByText("Auto-joins on acceptance")).toBeInTheDocument();

    const emailInput = screen.getByPlaceholderText(
      "Enter emails separated by comma or space..."
    );
    fireEvent.change(emailInput, {
      target: { value: "colleague@example.com" },
    });
    fireEvent.keyDown(emailInput, { key: "Enter", code: "Enter" });

    const sendBtn = screen.getByRole("button", { name: /Send Invitations/i });
    fireEvent.click(sendBtn);

    await waitFor(() => {
      expect(mockCreateBatchInvites).toHaveBeenCalledWith(
        ["colleague@example.com"],
        "MEMBER",
        undefined,
        [10]
      );
    });
  });
});
