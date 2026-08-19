import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import JoinPage from "./page";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import { useParams, useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";

jest.mock("next/navigation", () => ({
  useParams: jest.fn(),
  useRouter: jest.fn(),
}));

jest.mock("@/context/AuthContext", () => ({
  useAuth: jest.fn(),
}));

jest.mock("@/context/WorkspaceContext", () => ({
  useWorkspace: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

describe("JoinPage", () => {
  const mockPush = jest.fn();
  const mockAcceptInvite = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (useParams as jest.Mock).mockReturnValue({ token: "test-token-123" });
    (useRouter as jest.Mock).mockReturnValue({ push: mockPush });
    (useWorkspace as jest.Mock).mockReturnValue({
      acceptInvite: mockAcceptInvite,
      acceptInvitation: mockAcceptInvite,
    });
    sessionStorage.clear();
  });

  it("renders workspace preview and Accept & Join button for authenticated user", async () => {
    (useAuth as jest.Mock).mockReturnValue({ isAuthenticated: true });
    (apiFetch as jest.Mock).mockResolvedValue({
      id: 1,
      workspaceName: "Acme Workspace",
      workspaceSlug: "acme-workspace",
      token: "test-token-123",
      role: "MEMBER",
      status: "PENDING",
      inviterName: "alice_admin",
      customMessage: "Welcome to our engineering team!",
      initialChannels: [
        { id: 10, name: "general", type: "PUBLIC" },
        { id: 11, name: "backend-squad", type: "PRIVATE" },
      ],
    });

    render(<JoinPage />);

    await waitFor(() => {
      expect(screen.getByText("Join Acme Workspace")).toBeInTheDocument();
    });

    expect(screen.getByText("@alice_admin")).toBeInTheDocument();
    expect(
      screen.getByText(/Welcome to our engineering team!/i)
    ).toBeInTheDocument();
    expect(screen.getByText("general")).toBeInTheDocument();
    expect(screen.getByText("backend-squad")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /accept & join acme workspace/i })
    ).toBeInTheDocument();
  });

  it("clicking Accept & Join accepts invite and routes to workspace page", async () => {
    (useAuth as jest.Mock).mockReturnValue({ isAuthenticated: true });
    (apiFetch as jest.Mock).mockResolvedValue({
      id: 1,
      workspaceName: "Acme Workspace",
      workspaceSlug: "acme-workspace",
      token: "test-token-123",
      role: "MEMBER",
      status: "PENDING",
    });
    mockAcceptInvite.mockResolvedValue({
      id: 1,
      name: "Acme Workspace",
      slug: "acme-workspace",
    });

    render(<JoinPage />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /accept & join acme workspace/i })
      ).toBeInTheDocument();
    });

    fireEvent.click(
      screen.getByRole("button", { name: /accept & join acme workspace/i })
    );

    await waitFor(() => {
      expect(mockAcceptInvite).toHaveBeenCalledWith("test-token-123");
      expect(mockPush).toHaveBeenCalledWith("/w/acme-workspace");
    });
  });

  it("displays error feedback when invitation is invalid or expired", async () => {
    (useAuth as jest.Mock).mockReturnValue({ isAuthenticated: true });
    (apiFetch as jest.Mock).mockRejectedValue(
      new Error("This invitation has expired")
    );

    render(<JoinPage />);

    await waitFor(() => {
      expect(
        screen.getByText("This invitation has expired")
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole("button", { name: /accept & join/i })
    ).not.toBeInTheDocument();
  });

  it("displays error feedback when shareable link is deactivated", async () => {
    (useAuth as jest.Mock).mockReturnValue({ isAuthenticated: true });
    (apiFetch as jest.Mock).mockRejectedValue(
      new Error("This shareable join link has been deactivated")
    );

    render(<JoinPage />);

    await waitFor(() => {
      expect(
        screen.getByText("This shareable join link has been deactivated")
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole("button", { name: /accept & join/i })
    ).not.toBeInTheDocument();
  });

  it("prompts unauthenticated user to sign in or create account with redirect destination preserved", async () => {
    (useAuth as jest.Mock).mockReturnValue({ isAuthenticated: false });
    (apiFetch as jest.Mock).mockResolvedValue({
      id: 1,
      workspaceName: "Acme Workspace",
      workspaceSlug: "acme-workspace",
      token: "test-token-123",
      role: "MEMBER",
      status: "PENDING",
    });

    render(<JoinPage />);

    await waitFor(() => {
      expect(screen.getByText("Join Acme Workspace")).toBeInTheDocument();
    });

    const signInBtn = screen.getByRole("button", {
      name: /sign in to accept/i,
    });
    const signUpBtn = screen.getByRole("button", {
      name: /create account to join/i,
    });
    expect(signInBtn).toBeInTheDocument();
    expect(signUpBtn).toBeInTheDocument();

    fireEvent.click(signUpBtn);

    expect(sessionStorage.getItem("auth_redirect")).toBe(
      "/join/test-token-123"
    );
    expect(mockPush).toHaveBeenCalledWith(
      "/?redirect=/join/test-token-123&mode=signup"
    );
  });
});
