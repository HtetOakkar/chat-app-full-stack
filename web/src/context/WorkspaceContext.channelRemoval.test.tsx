import React from "react";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
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
import ChannelRemovalNotice from "@/components/ChannelRemovalNotice";

jest.mock("./AuthContext", () => ({ useAuth: jest.fn() }));
jest.mock("./ConnectionContext", () => ({ useConnection: jest.fn() }));
jest.mock("@/lib/api", () => ({ apiFetch: jest.fn() }));

const REMOVAL_QUEUE = "/user/queue/channel-removals";

const acme: WorkspaceDto = {
  id: 1,
  name: "Acme Corp",
  slug: "acme-corp",
  ownerId: 20,
  role: "MEMBER",
  memberCount: 3,
  channelCount: 3,
  createdAt: "2026-08-19T00:00:00Z",
};

const channel = (
  id: number,
  slug: string,
  type: "PUBLIC" | "PRIVATE" = "PUBLIC"
): ChannelDto => ({
  id,
  workspaceId: 1,
  conversationId: id * 10,
  name: slug,
  slug,
  type,
  isArchived: false,
  isMember: true,
  memberCount: 3,
  createdAt: "2026-08-19T00:00:00Z",
});

const general = channel(11, "general");
const random = channel(12, "random");
const secretOps = channel(13, "secret-ops", "PRIVATE");

const dmWithCarol: DirectMessageDto = {
  conversationId: 300,
  workspaceId: 1,
  targetMember: {
    id: 7,
    workspaceId: 1,
    userId: 30,
    username: "carol",
    role: "ADMIN",
    joinedAt: "2026-08-19T00:00:00Z",
  },
  createdAt: "2026-08-19T00:00:00Z",
};

/** The "removed from channel" notice as the server sends it to the removed User only. */
const removalNotice = {
  type: "REMOVED_FROM_CHANNEL",
  workspaceId: 1,
  channelId: secretOps.id,
  channelName: "secret-ops",
  removedByUserId: 30,
  removedByUsername: "carol",
  removedByFullName: "Carol Admin",
};

let routes: Record<string, unknown>;
let handlers: Record<string, (frame: { body: string }) => void>;

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

function Probe() {
  const {
    channels,
    currentChannel,
    currentDirectMessage,
    switchChannel,
    switchDirectMessage,
  } = useWorkspace();
  return (
    <div>
      <p data-testid="channels">{channels.map((c) => c.slug).join(",")}</p>
      <p data-testid="channel">{currentChannel?.slug ?? "none"}</p>
      <p data-testid="dm">
        {currentDirectMessage?.targetMember.username ?? "none"}
      </p>
      <button onClick={() => switchChannel(random.id)}>open random</button>
      <button onClick={() => switchChannel(secretOps.id)}>open secret</button>
      <button onClick={() => switchDirectMessage(30)}>open carol</button>
      <ChannelRemovalNotice />
    </div>
  );
}

async function renderWorkspace() {
  render(
    <WorkspaceProvider>
      <Probe />
    </WorkspaceProvider>
  );
  await waitFor(() =>
    expect(screen.getByTestId("channels")).toHaveTextContent(
      "general,random,secret-ops"
    )
  );
  await waitFor(() => expect(handlers[REMOVAL_QUEUE]).toBeDefined());
}

/** The server has already revoked membership by the time any frame arrives. */
function serverRemovesMeFromSecretOps() {
  routes["/api/workspaces/1/channels"] = [general, random];
}

async function deliver(destination: string, payload: unknown) {
  await act(async () => {
    handlers[destination]({ body: JSON.stringify(payload) });
  });
}

describe("Removed-from-channel notice (#82, #96)", () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    routes = {
      "/api/workspaces/me": [acme],
      "/api/workspaces/1/channels": [general, random, secretOps],
      "/api/workspaces/1/dm": [dmWithCarol],
      "/api/workspaces/1/members": [],
      "/api/v1/users/online": [],
    };
    handlers = {};
    (useAuth as jest.Mock).mockReturnValue({
      userId: 10,
      username: "bob",
      isAuthenticated: true,
    });
    (useConnection as jest.Mock).mockReturnValue({
      connected: true,
      subscribe: jest.fn(
        (destination: string, handler: (frame: { body: string }) => void) => {
          handlers[destination] = handler;
          return () => {
            if (handlers[destination] === handler) delete handlers[destination];
          };
        }
      ),
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

  it("tells me who removed me while I am viewing a different Channel, and drops the Channel from my sidebar", async () => {
    setLayout("wide");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open random"));

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);

    expect(
      screen.getByText("@carol removed you from #secret-ops")
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("channels")).toHaveTextContent(
        /^general,random$/
      )
    );
    expect(screen.getByTestId("channel")).toHaveTextContent("random");
  });

  it("tells me who removed me while I am in a DM, without closing the DM", async () => {
    setLayout("mobile");
    await renderWorkspace();
    await waitFor(() =>
      expect(apiFetch).toHaveBeenCalledWith("/api/workspaces/1/dm")
    );
    fireEvent.click(screen.getByText("open carol"));
    await waitFor(() =>
      expect(screen.getByTestId("dm")).toHaveTextContent("carol")
    );

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);

    expect(
      screen.getByText("@carol removed you from #secret-ops")
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("channels")).toHaveTextContent(
        /^general,random$/
      )
    );
    expect(screen.getByTestId("dm")).toHaveTextContent("carol");
  });

  it("takes me back to the sidebar on mobile when I was viewing the Channel", async () => {
    setLayout("mobile");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open secret"));
    expect(screen.getByTestId("channel")).toHaveTextContent("secret-ops");

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);

    expect(screen.getByTestId("channel")).toHaveTextContent("none");
    expect(
      screen.getByText("@carol removed you from #secret-ops")
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("channels")).toHaveTextContent(
        /^general,random$/
      )
    );
    expect(screen.getByTestId("channel")).toHaveTextContent("none");
  });

  it("falls back to #general on wide layouts when I was viewing the Channel", async () => {
    setLayout("wide");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open secret"));

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);

    expect(screen.getByTestId("channel")).toHaveTextContent("general");
    expect(
      screen.getByText("@carol removed you from #secret-ops")
    ).toBeInTheDocument();
  });

  it("navigates away when the Workspace removal event for me arrives before the notice", async () => {
    setLayout("mobile");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open secret"));

    serverRemovesMeFromSecretOps();
    await deliver("/topic/workspace.1.events", {
      type: "CHANNEL_MEMBER_REMOVED",
      workspaceId: 1,
      channelId: secretOps.id,
      userId: 10,
      isSelfLeave: false,
    });

    expect(screen.getByTestId("channel")).toHaveTextContent("none");
    await waitFor(() =>
      expect(screen.getByTestId("channels")).toHaveTextContent(
        /^general,random$/
      )
    );
    // The Workspace-wide event never names the remover, so no notice yet.
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("leaves my view alone when someone else is removed", async () => {
    setLayout("wide");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open secret"));

    await deliver("/topic/workspace.1.events", {
      type: "CHANNEL_MEMBER_REMOVED",
      workspaceId: 1,
      channelId: secretOps.id,
      userId: 99,
      isSelfLeave: false,
    });

    expect(screen.getByTestId("channel")).toHaveTextContent("secret-ops");
    expect(screen.getByTestId("channels")).toHaveTextContent(
      "general,random,secret-ops"
    );
  });

  it("announces the notice in a live region that was already mounted before it arrived", async () => {
    setLayout("wide");
    await renderWorkspace();

    // Screen readers skip live regions inserted already filled, so the
    // region must be waiting, empty, before the notice comes in.
    const region = screen.getByRole("status");
    expect(region).toBeEmptyDOMElement();

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);

    expect(screen.getByRole("status")).toBe(region);
    expect(region).toHaveTextContent("@carol removed you from #secret-ops");

    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(screen.getByRole("status")).toBe(region);
    expect(region).toBeEmptyDOMElement();
  });

  it("can be dismissed", async () => {
    setLayout("wide");
    await renderWorkspace();

    serverRemovesMeFromSecretOps();
    await deliver(REMOVAL_QUEUE, removalNotice);
    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));

    expect(
      screen.queryByText("@carol removed you from #secret-ops")
    ).not.toBeInTheDocument();
  });

  it("still tells me when the Channel belongs to a Workspace I am not viewing", async () => {
    setLayout("wide");
    await renderWorkspace();
    fireEvent.click(screen.getByText("open secret"));

    await deliver(REMOVAL_QUEUE, {
      ...removalNotice,
      workspaceId: 2,
      channelId: 21,
      channelName: "beta-secrets",
    });

    expect(
      screen.getByText("@carol removed you from #beta-secrets")
    ).toBeInTheDocument();
    expect(screen.getByTestId("channel")).toHaveTextContent("secret-ops");
    expect(screen.getByTestId("channels")).toHaveTextContent(
      "general,random,secret-ops"
    );
  });
});
