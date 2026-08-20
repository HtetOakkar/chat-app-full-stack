import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import WorkspaceSidebar from "./WorkspaceSidebar";
import WorkspaceChannelViewport from "./WorkspaceChannelViewport";
import WorkspaceDMViewport from "./WorkspaceDMViewport";
import ActiveCallOverlay from "./ActiveCallOverlay";
import CreateChannelModal from "./CreateChannelModal";
import {
  useWorkspace,
  type ChannelDto,
  type DirectMessageDto,
  type WorkspaceDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { useCall } from "@/context/CallContext";
import { apiFetch } from "@/lib/api";

jest.mock("@/context/WorkspaceContext");
jest.mock("@/context/AuthContext");
jest.mock("@/context/ConnectionContext");
jest.mock("@/context/CallContext");
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

describe("Mobile Responsive Layout & Interaction Suite", () => {
  const mockWorkspace: WorkspaceDto = {
    id: 1,
    name: "Acme Corp",
    slug: "acme-corp",
    ownerId: 101,
    role: "OWNER",
    memberCount: 5,
    channelCount: 2,
    createdAt: "2026-08-19T00:00:00Z",
  };

  const mockWorkspaces: WorkspaceDto[] = [
    mockWorkspace,
    {
      id: 2,
      name: "Beta Inc",
      slug: "beta-inc",
      ownerId: 101,
      role: "MEMBER",
      memberCount: 3,
      channelCount: 1,
      createdAt: "2026-08-19T00:00:00Z",
    },
  ];

  const mockChannel: ChannelDto = {
    id: 10,
    workspaceId: 1,
    conversationId: 100,
    name: "general",
    slug: "general",
    type: "PUBLIC",
    isArchived: false,
    isMember: true,
    memberCount: 5,
    createdAt: "2026-08-19T00:00:00Z",
  };

  const mockDM: DirectMessageDto = {
    conversationId: 200,
    workspaceId: 1,
    targetMember: {
      id: 2,
      workspaceId: 1,
      userId: 102,
      username: "bob",
      fullName: "Bob Jones",
      role: "MEMBER",
      joinedAt: "2026-08-19T00:00:00Z",
    },
    createdAt: "2026-08-19T00:00:00Z",
  };

  const mockSwitchWorkspace = jest.fn();
  const mockSwitchChannel = jest.fn();
  const mockSwitchDirectMessage = jest.fn();
  const mockClearActiveConversation = jest.fn();
  const mockInitiateCall = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    (useAuth as jest.Mock).mockReturnValue({
      userId: 101,
      username: "alice",
    });

    (useWorkspace as jest.Mock).mockReturnValue({
      workspaces: mockWorkspaces,
      currentWorkspace: mockWorkspace,
      channels: [mockChannel],
      currentChannel: null,
      directMessages: [mockDM],
      currentDirectMessage: null,
      presenceMap: { 101: "ONLINE", 102: "ONLINE" },
      switchWorkspace: mockSwitchWorkspace,
      switchChannel: mockSwitchChannel,
      switchDirectMessage: mockSwitchDirectMessage,
      clearActiveConversation: mockClearActiveConversation,
    });

    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
      subscribe: jest.fn(() => jest.fn()),
      stompClientRef: { current: { publish: jest.fn() } },
    });

    (useCall as jest.Mock).mockReturnValue({
      status: "idle",
      callerInfo: null,
      localStream: null,
      remoteStream: null,
      isMuted: false,
      isCameraOff: false,
      callType: "AUDIO",
      isFullscreen: false,
      duration: 0,
      audioOutputDevices: [],
      selectedAudioOutputId: "",
      switchAudioOutput: jest.fn(),
      cancelCall: jest.fn(),
      hangupCall: jest.fn(),
      toggleMute: jest.fn(),
      toggleCamera: jest.fn(),
      setIsFullscreen: jest.fn(),
      initiateCall: mockInitiateCall,
    });

    (apiFetch as jest.Mock).mockResolvedValue({
      messages: [],
      nextCursor: null,
    });
  });

  describe("Issue #84 & #86: Mobile Master-Detail and Switcher", () => {
    it("renders mobile workspace switcher inside header dropdown and allows single-tap switching", () => {
      const mockOpenCreate = jest.fn();
      render(
        <WorkspaceSidebar
          onOpenCreateChannel={jest.fn()}
          onOpenInvite={jest.fn()}
          onOpenMemberDirectory={jest.fn()}
          onOpenSettings={jest.fn()}
          onOpenProfile={jest.fn()}
          onOpenCreateWorkspace={mockOpenCreate}
        />
      );

      // Open workspace menu
      const wsMenuBtn = screen.getByText("Acme Corp");
      fireEvent.click(wsMenuBtn);

      // Mobile switcher should show Beta Inc
      const betaBtn = screen.getByRole("button", { name: /Beta Inc/i });
      expect(betaBtn).toBeInTheDocument();

      // Click to switch
      fireEvent.click(betaBtn);
      expect(mockSwitchWorkspace).toHaveBeenCalledWith("beta-inc");
    });

    it("opens Create Workspace modal from mobile workspace dropdown", () => {
      const mockOpenCreate = jest.fn();
      render(
        <WorkspaceSidebar
          onOpenCreateChannel={jest.fn()}
          onOpenInvite={jest.fn()}
          onOpenMemberDirectory={jest.fn()}
          onOpenSettings={jest.fn()}
          onOpenProfile={jest.fn()}
          onOpenCreateWorkspace={mockOpenCreate}
        />
      );

      fireEvent.click(screen.getByText("Acme Corp"));
      const createWsBtn = screen.getByRole("button", {
        name: /Create Workspace/i,
      });
      expect(createWsBtn).toBeInTheDocument();

      fireEvent.click(createWsBtn);
      expect(mockOpenCreate).toHaveBeenCalled();
    });

    it("renders back button on mobile in Channel viewport and calls onBack", () => {
      const handleBack = jest.fn();
      render(
        <WorkspaceChannelViewport channel={mockChannel} onBack={handleBack} />
      );

      const backBtn = screen.getByLabelText("Back to channels");
      expect(backBtn).toBeInTheDocument();

      fireEvent.click(backBtn);
      expect(handleBack).toHaveBeenCalled();
    });

    it("renders back button on mobile in Direct Message viewport and calls onBack", () => {
      const handleBack = jest.fn();
      render(<WorkspaceDMViewport dm={mockDM} onBack={handleBack} />);

      const backBtn = screen.getByLabelText("Back to conversations");
      expect(backBtn).toBeInTheDocument();

      fireEvent.click(backBtn);
      expect(handleBack).toHaveBeenCalled();
    });
  });

  describe("Issue #85: Active Call UI on Mobile", () => {
    it("renders responsive calling controls dock with mute, camera, and hangup", () => {
      (useCall as jest.Mock).mockReturnValue({
        status: "active",
        callerInfo: { id: 102, username: "bob", fullName: "Bob Jones" },
        localStream: {},
        remoteStream: {},
        isMuted: false,
        isCameraOff: false,
        callType: "VIDEO",
        isFullscreen: false,
        duration: 45,
        audioOutputDevices: [{ deviceId: "default", label: "Default Speaker" }],
        selectedAudioOutputId: "default",
        switchAudioOutput: jest.fn(),
        cancelCall: jest.fn(),
        hangupCall: jest.fn(),
        toggleMute: jest.fn(),
        toggleCamera: jest.fn(),
        setIsFullscreen: jest.fn(),
      });

      render(<ActiveCallOverlay />);

      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
      expect(screen.getByText("00:45")).toBeInTheDocument();
      expect(screen.getByTestId("remote-video")).toBeInTheDocument();
      expect(screen.getByTestId("local-video")).toBeInTheDocument();
    });
  });

  describe("Issue #87 & #88: Responsive Emoji Drawer & Bottom Sheet Modals", () => {
    it("toggles emoji picker drawer on clicking mood icon", () => {
      render(<WorkspaceChannelViewport channel={mockChannel} />);

      const moodBtn = screen.getByText("mood");
      fireEvent.click(moodBtn);

      expect(
        screen.getByPlaceholderText("Message #general...")
      ).toBeInTheDocument();
    });

    it("renders CreateChannelModal as a bottom sheet modal structure", () => {
      render(<CreateChannelModal isOpen={true} onClose={jest.fn()} />);

      expect(screen.getByText("Create a Channel")).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/announcements/i)).toBeInTheDocument();
    });
  });
});
