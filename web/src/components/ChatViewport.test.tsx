import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ChatViewport from "./ChatViewport";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { useMessageStore } from "@/context/MessageStore";
import { usePresence } from "@/context/PresenceContext";

window.HTMLElement.prototype.scrollIntoView = jest.fn();

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
});
