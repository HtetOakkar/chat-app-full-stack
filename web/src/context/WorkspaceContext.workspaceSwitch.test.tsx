import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import {
  WorkspaceProvider,
  useWorkspace,
  type ChannelDto,
  type DirectMessageDto,
  type WorkspaceDto,
} from "./WorkspaceContext";
import { useAuth } from "./AuthContext";
import { useConnection } from "./ConnectionContext";
import { apiFetch } from "@/lib/api";

jest.mock("./AuthContext", () => ({ useAuth: jest.fn() }));
jest.mock("./ConnectionContext", () => ({ useConnection: jest.fn() }));
jest.mock("@/lib/api", () => ({ apiFetch: jest.fn() }));

const acme: WorkspaceDto = {
  id: 1,
  name: "Acme Corp",
  slug: "acme-corp",
  ownerId: 10,
  role: "OWNER",
  memberCount: 2,
  channelCount: 2,
  createdAt: "2026-08-19T00:00:00Z",
};

const beta: WorkspaceDto = {
  id: 2,
  name: "Beta Labs",
  slug: "beta-labs",
  ownerId: 20,
  role: "MEMBER",
  memberCount: 2,
  channelCount: 1,
  createdAt: "2026-08-19T00:00:00Z",
};

const channel = (
  id: number,
  workspaceId: number,
  slug: string
): ChannelDto => ({
  id,
  workspaceId,
  conversationId: id * 10,
  name: slug,
  slug,
  type: "PUBLIC",
  isArchived: false,
  isMember: true,
  memberCount: 2,
  createdAt: "2026-08-19T00:00:00Z",
});

const acmeGeneral = channel(11, 1, "general");
const acmeRandom = channel(12, 1, "random");
const betaGeneral = channel(21, 2, "general");

const acmeDmWithBob: DirectMessageDto = {
  conversationId: 300,
  workspaceId: 1,
  targetMember: {
    id: 5,
    workspaceId: 1,
    userId: 30,
    username: "bob",
    role: "MEMBER",
    joinedAt: "2026-08-19T00:00:00Z",
  },
  createdAt: "2026-08-19T00:00:00Z",
};

const routes: Record<string, unknown> = {
  "/api/workspaces/me": [acme, beta],
  "/api/workspaces/1/channels": [acmeGeneral, acmeRandom],
  "/api/workspaces/2/channels": [betaGeneral],
  "/api/workspaces/1/dm": [acmeDmWithBob],
  "/api/workspaces/2/dm": [],
  "/api/workspaces/1/members": [],
  "/api/workspaces/2/members": [],
};

function setLayout(layout: "mobile" | "wide") {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: jest.fn((query: string) => ({
      matches: layout === "wide" && query === "(min-width: 768px)",
      media: query,
      onchange: null,
      addListener: jest.fn(),
      removeListener: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
      dispatchEvent: jest.fn(),
    })),
  });
}

function ActiveConversationProbe() {
  const {
    currentWorkspace,
    channels,
    currentChannel,
    currentDirectMessage,
    switchWorkspace,
    switchChannel,
    switchDirectMessage,
  } = useWorkspace();
  return (
    <div>
      <p data-testid="workspace">{currentWorkspace?.slug ?? "none"}</p>
      <p data-testid="channels">
        {channels.map((c) => `${c.workspaceId}:${c.slug}`).join(",")}
      </p>
      <p data-testid="channel">
        {currentChannel
          ? `${currentChannel.workspaceId}:${currentChannel.slug}`
          : "none"}
      </p>
      <p data-testid="dm">
        {currentDirectMessage?.targetMember.username ?? "none"}
      </p>
      <button onClick={() => switchChannel(acmeRandom.id)}>open random</button>
      <button onClick={() => switchDirectMessage(30)}>open bob</button>
      <button onClick={() => switchWorkspace("beta-labs")}>
        switch to beta
      </button>
    </div>
  );
}

function renderWorkspace() {
  render(
    <WorkspaceProvider>
      <ActiveConversationProbe />
    </WorkspaceProvider>
  );
}

async function waitForChannelsOf(workspaceId: number) {
  const expected = (
    routes[`/api/workspaces/${workspaceId}/channels`] as ChannelDto[]
  )
    .map((c) => `${c.workspaceId}:${c.slug}`)
    .join(",");
  await waitFor(() =>
    expect(screen.getByTestId("channels")).toHaveTextContent(expected)
  );
}

describe("Workspace switch lands per layout (#86, #92)", () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    (useAuth as jest.Mock).mockReturnValue({
      userId: 10,
      username: "alice",
      isAuthenticated: true,
    });
    (useConnection as jest.Mock).mockReturnValue({
      connected: false,
      subscribe: jest.fn(() => () => {}),
      stompClientRef: { current: null },
    });
    (apiFetch as jest.Mock).mockImplementation((url: string) =>
      Promise.resolve(routes[url] ?? [])
    );
  });

  afterEach(() => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      writable: true,
      value: originalMatchMedia,
    });
    jest.clearAllMocks();
  });

  describe("mobile layout", () => {
    beforeEach(() => setLayout("mobile"));

    it("lands on the sidebar with no conversation open when the Workspace first loads", async () => {
      renderWorkspace();

      await waitForChannelsOf(1);

      expect(screen.getByTestId("workspace")).toHaveTextContent("acme-corp");
      expect(screen.getByTestId("channel")).toHaveTextContent("none");
      expect(screen.getByTestId("dm")).toHaveTextContent("none");
    });

    it("clears the open Channel and stays on the sidebar after switching Workspace", async () => {
      renderWorkspace();
      await waitForChannelsOf(1);

      fireEvent.click(screen.getByText("open random"));
      expect(screen.getByTestId("channel")).toHaveTextContent("1:random");

      fireEvent.click(screen.getByText("switch to beta"));
      await waitForChannelsOf(2);

      expect(screen.getByTestId("workspace")).toHaveTextContent("beta-labs");
      expect(screen.getByTestId("channel")).toHaveTextContent("none");
      expect(screen.getByTestId("dm")).toHaveTextContent("none");
    });

    it("clears the open DM and stays on the sidebar after switching Workspace", async () => {
      renderWorkspace();
      await waitForChannelsOf(1);
      await waitFor(() =>
        expect(apiFetch).toHaveBeenCalledWith("/api/workspaces/1/dm")
      );

      fireEvent.click(screen.getByText("open bob"));
      await waitFor(() =>
        expect(screen.getByTestId("dm")).toHaveTextContent("bob")
      );

      fireEvent.click(screen.getByText("switch to beta"));
      await waitForChannelsOf(2);

      expect(screen.getByTestId("workspace")).toHaveTextContent("beta-labs");
      expect(screen.getByTestId("channel")).toHaveTextContent("none");
      expect(screen.getByTestId("dm")).toHaveTextContent("none");
    });
  });

  describe("wide layout", () => {
    beforeEach(() => setLayout("wide"));

    it("auto-selects #general when the Workspace first loads", async () => {
      renderWorkspace();

      await waitFor(() =>
        expect(screen.getByTestId("channel")).toHaveTextContent("1:general")
      );
    });

    it("clears the open conversation on switch and auto-selects the new Workspace's #general", async () => {
      renderWorkspace();
      await waitForChannelsOf(1);

      fireEvent.click(screen.getByText("open random"));
      expect(screen.getByTestId("channel")).toHaveTextContent("1:random");

      fireEvent.click(screen.getByText("switch to beta"));

      await waitFor(() =>
        expect(screen.getByTestId("channel")).toHaveTextContent("2:general")
      );
      expect(screen.getByTestId("workspace")).toHaveTextContent("beta-labs");
      expect(screen.getByTestId("dm")).toHaveTextContent("none");
    });
  });
});
