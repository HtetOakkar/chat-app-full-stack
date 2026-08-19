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
    });

    render(<JoinPage />);

    await waitFor(() => {
      expect(screen.getByText("Join Acme Workspace")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/You have been invited to join this workspace as a/i)
    ).toBeInTheDocument();
    expect(screen.getByText("member")).toBeInTheDocument();
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

  it("prompts unauthenticated user to sign in with redirect destination preserved", async () => {
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
    expect(signInBtn).toBeInTheDocument();

    fireEvent.click(signInBtn);

    expect(sessionStorage.getItem("auth_redirect")).toBe(
      "/join/test-token-123"
    );
    expect(mockPush).toHaveBeenCalledWith("/?redirect=/join/test-token-123");
  });
});
