import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ChatViewport from "./ChatViewport";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { useMessageStore } from "@/context/MessageStore";
import { usePresence } from "@/context/PresenceContext";
import { useCall } from "@/context/CallContext";

window.HTMLElement.prototype.scrollIntoView = jest.fn();

jest.mock("@/context/CallContext", () => ({
  useCall: jest.fn(),
}));

jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));
jest.mock("@/context/ConnectionContext", () => ({
  useConnection: jest.fn(),
}));
jest.mock("@/context/MessageStore", () => ({
  useMessageStore: jest.fn(),
}));
jest.mock("@/context/PresenceContext", () => ({
  usePresence: jest.fn(),
}));
jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn().mockResolvedValue([]),
}));

describe("ChatViewport", () => {
  const mockSendPublicMessage = jest.fn();
  const mockSendPrivateMessage = jest.fn();
  const mockSendTypingIndicator = jest.fn();
  const mockLoadPublicHistory = jest.fn();
  const mockLoadPrivateHistory = jest.fn();

  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({ userId: 1 });
    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
    });
    (useMessageStore as jest.Mock).mockReturnValue({
      publicMessages: [],
      privateMessages: {},
      sendPublicMessage: mockSendPublicMessage,
      sendPrivateMessage: mockSendPrivateMessage,
      loadPublicHistory: mockLoadPublicHistory,
      loadPrivateHistory: mockLoadPrivateHistory,
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });
    (usePresence as jest.Mock).mockReturnValue({
      onlineUsers: {},
      typingUsers: {},
      sendTypingIndicator: mockSendTypingIndicator,
    });
    (useCall as jest.Mock).mockReturnValue({
      initiateCall: jest.fn(),
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it("displays typing indicator when the active chat user is typing", () => {
    (usePresence as jest.Mock).mockReturnValue({
      onlineUsers: {},
      typingUsers: { 2: true },
      sendTypingIndicator: mockSendTypingIndicator,
    });

    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    expect(screen.getByText("user2 is typing...")).toBeInTheDocument();
  });

  it("sends typing indicator when user types in the input", async () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    const input = screen.getByPlaceholderText("Compose message for user2...");
    fireEvent.change(input, { target: { value: "h" } });

    await waitFor(() => {
      expect(mockSendTypingIndicator).toHaveBeenCalledWith(2, true);
    });
  });

  it("sends typing indicator false immediately after sending a private message", async () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    const input = screen.getByPlaceholderText("Compose message for user2...");
    fireEvent.change(input, { target: { value: "hello" } });

    const sendButton = screen.getByRole("button", { name: /send/i });
    fireEvent.click(sendButton);

    await waitFor(() => {
      expect(mockSendPrivateMessage).toHaveBeenCalledWith(2, "hello");
      expect(mockSendTypingIndicator).toHaveBeenCalledWith(2, false);
    });
  });

  it("renders call buttons for accepted contacts", () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    expect(screen.getByTitle("Audio Call")).toBeInTheDocument();
    expect(screen.getByTitle("Video Call")).toBeInTheDocument();
  });

  it("does not render call buttons for pending contact requests", () => {
    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "PENDING_REQUEST",
        }}
        onBannerAction={jest.fn()}
      />
    );

    expect(screen.queryByTitle("Audio Call")).toBeNull();
    expect(screen.queryByTitle("Video Call")).toBeNull();
  });

  it("renders AUDIO call record as a human-friendly bubble, not raw JSON", () => {
    const callRecordMessage = {
      id: 100,
      content: '{"outcome":"completed","duration":75,"videoUsed":false}',
      senderId: 1,
      senderUsername: "user1",
      senderFullName: "User One",
      recipientId: 2,
      timestamp: new Date().toISOString(),
      messageType: "AUDIO",
      callOutcome: "completed",
      callDuration: 75,
      videoUsed: false,
    };

    (useMessageStore as jest.Mock).mockReturnValue({
      publicMessages: [],
      privateMessages: { 2: [callRecordMessage] },
      sendPublicMessage: jest.fn(),
      sendPrivateMessage: jest.fn(),
      loadPublicHistory: mockLoadPublicHistory,
      loadPrivateHistory: mockLoadPrivateHistory,
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });

    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    // Should NOT display raw JSON
    expect(screen.queryByText(/{"outcome"/)).toBeNull();
    // Should display human-friendly text
    expect(screen.getByText(/call ended/i)).toBeInTheDocument();
    expect(screen.getByText(/01:15/)).toBeInTheDocument();
  });

  it("renders VIDEO call record with missed outcome as human-friendly bubble", () => {
    const callRecordMessage = {
      id: 101,
      content: '{"outcome":"missed","duration":0,"videoUsed":true}',
      senderId: 2,
      senderUsername: "user2",
      senderFullName: "User Two",
      recipientId: 1,
      timestamp: new Date().toISOString(),
      messageType: "VIDEO",
      callOutcome: "missed",
      callDuration: 0,
      videoUsed: true,
    };

    (useMessageStore as jest.Mock).mockReturnValue({
      publicMessages: [],
      privateMessages: { 2: [callRecordMessage] },
      sendPublicMessage: jest.fn(),
      sendPrivateMessage: jest.fn(),
      loadPublicHistory: mockLoadPublicHistory,
      loadPrivateHistory: mockLoadPrivateHistory,
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });

    render(
      <ChatViewport
        activeChat={{
          id: 2,
          isPublic: false,
          username: "user2",
          status: "ACCEPTED",
        }}
        onBannerAction={jest.fn()}
      />
    );

    // Should NOT display raw JSON
    expect(screen.queryByText(/{"outcome"/)).toBeNull();
    // Should display outcome
    expect(screen.getByText(/missed/i)).toBeInTheDocument();
  });
});
