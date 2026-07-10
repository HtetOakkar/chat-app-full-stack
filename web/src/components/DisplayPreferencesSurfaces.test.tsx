import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import Sidebar from "./Sidebar";
import ChatViewport from "./ChatViewport";
import IncomingCallModal from "./IncomingCallModal";
import DisplayPreferencesControls from "./DisplayPreferencesControls";
import { DisplayPreferencesProvider } from "@/context/DisplayPreferencesContext";
import { useAuth } from "@/context/AuthContext";
import { usePresence } from "@/context/PresenceContext";
import { useConnection } from "@/context/ConnectionContext";
import { useMessageStore } from "@/context/MessageStore";
import { useCall } from "@/context/CallContext";
import { apiFetch } from "@/lib/api";

jest.mock("@/context/AuthContext", () => ({ useAuth: jest.fn() }));
jest.mock("@/context/PresenceContext", () => ({ usePresence: jest.fn() }));
jest.mock("@/context/ConnectionContext", () => ({ useConnection: jest.fn() }));
jest.mock("@/context/MessageStore", () => ({ useMessageStore: jest.fn() }));
jest.mock("@/context/CallContext", () => ({ useCall: jest.fn() }));
jest.mock("@/lib/api", () => ({ apiFetch: jest.fn() }));

const renderWithPreferences = (ui: React.ReactElement) =>
  render(<DisplayPreferencesProvider>{ui}</DisplayPreferencesProvider>);

describe("localized signed-in surfaces", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.matchMedia = jest.fn().mockReturnValue({
      matches: false,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    });
    (useAuth as jest.Mock).mockReturnValue({ userId: 1 });
    (usePresence as jest.Mock).mockReturnValue({
      onlineUsers: {},
      typingUsers: {},
      sendTypingIndicator: jest.fn(),
    });
    (useConnection as jest.Mock).mockReturnValue({ connected: true });
    (useMessageStore as jest.Mock).mockReturnValue({
      publicMessages: [
        {
          id: 1,
          senderId: 2,
          senderUsername: "alice",
          content: "hello exactly as typed",
          timestamp: "2026-07-08T10:00:00Z",
        },
      ],
      privateMessages: {},
      sendPublicMessage: jest.fn(),
      sendPrivateMessage: jest.fn(),
      loadPublicHistory: jest.fn(),
      loadPrivateHistory: jest.fn(),
      hasMorePublicHistory: false,
      hasMorePrivateHistory: {},
    });
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: { id: 9, fullName: "Alice Example" },
      callType: "VIDEO",
      acceptCall: jest.fn(),
      rejectCall: jest.fn(),
    });
    (apiFetch as jest.Mock).mockImplementation((url) => {
      if (url === "/api/v1/contacts") return Promise.resolve([]);
      if (url === "/api/v1/contacts/requests") return Promise.resolve([]);
      if (url === "/api/v1/contacts/blocked") return Promise.resolve([]);
      return Promise.resolve([]);
    });
  });

  it("switches sidebar app-owned copy to Burmese while preserving user names", async () => {
    renderWithPreferences(
      <>
        <DisplayPreferencesControls />
        <Sidebar
          activeChat={null}
          onSelectChat={jest.fn()}
          onSelectProfileUser={jest.fn()}
          refreshTrigger={0}
        />
      </>
    );

    await waitFor(() => expect(apiFetch).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText(/interface language/i), {
      target: { value: "my" },
    });

    expect(screen.getByText("အများသုံး စကားဝိုင်း")).toBeInTheDocument();
    expect(screen.getByText("Meow Chit Chat")).toBeInTheDocument();
  });

  it("switches chat and call labels while preserving message and profile text", () => {
    renderWithPreferences(
      <>
        <DisplayPreferencesControls />
        <ChatViewport
          activeChat={{
            id: 0,
            username: "Public Conversation",
            isPublic: true,
          }}
          onBannerAction={jest.fn()}
        />
        <IncomingCallModal />
      </>
    );

    fireEvent.change(screen.getByLabelText(/interface language/i), {
      target: { value: "my" },
    });

    expect(screen.getByText("အများသုံးအခန်း")).toBeInTheDocument();
    expect(screen.getByText("hello exactly as typed")).toBeInTheDocument();
    expect(screen.getByText("Alice Example")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /လက်ခံမည်/ })
    ).toBeInTheDocument();
  });
});
