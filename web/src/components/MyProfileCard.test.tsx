import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import MyProfileCard from "./MyProfileCard";
import { apiFetch } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  apiFetch: jest.fn(),
}));

describe("MyProfileCard", () => {
  beforeEach(() => {
    (apiFetch as jest.Mock).mockImplementation((url: string) => {
      if (url === "/api/v1/users/profile") {
        return Promise.resolve({
          username: "testuser",
          email: "test@example.com",
          fullName: "Test User",
          birthDate: "1990-01-01",
          emailVerified: true,
        });
      }
      if (url === "/api/v1/users/settings") {
        return Promise.resolve({
          sharePresence: true,
        });
      }
      return Promise.resolve({});
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.clearAllMocks();
  });

  it("loads and displays profile and settings data", async () => {
    render(<MyProfileCard onBack={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByDisplayValue("Test User")).toBeInTheDocument();
    });

    expect(screen.getByDisplayValue("test@example.com")).toBeInTheDocument();
    expect(screen.getByDisplayValue("1990-01-01")).toBeInTheDocument();
    expect(screen.getByRole("checkbox")).toBeChecked();
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/users/profile");
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/users/settings");
  });

  it("saves profile and settings successfully without error", async () => {
    (apiFetch as jest.Mock).mockImplementation(
      (url: string, options?: { method?: string; body?: string }) => {
        if (url === "/api/v1/users/profile" && options?.method === "PUT") {
          return Promise.resolve({
            username: "testuser",
            email: "updated@example.com",
            fullName: "Updated Name",
            birthDate: "1995-05-05",
            emailVerified: true,
          });
        }
        if (url === "/api/v1/users/settings" && options?.method === "PUT") {
          return Promise.resolve({
            sharePresence: false,
          });
        }
        if (url === "/api/v1/users/profile") {
          return Promise.resolve({
            username: "testuser",
            email: "test@example.com",
            fullName: "Test User",
            birthDate: "1990-01-01",
            emailVerified: true,
          });
        }
        if (url === "/api/v1/users/settings") {
          return Promise.resolve({
            sharePresence: true,
          });
        }
        return Promise.resolve({});
      }
    );

    render(<MyProfileCard onBack={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByDisplayValue("Test User")).toBeInTheDocument();
    });

    const nameInput = screen.getByLabelText(/full name/i);
    const presenceCheckbox = screen.getByRole("checkbox");

    fireEvent.change(nameInput, { target: { value: "Updated Name" } });
    fireEvent.click(presenceCheckbox);

    const saveButton = screen.getByRole("button", { name: /save changes/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(
        screen.getByText(/profile and settings updated successfully/i)
      ).toBeInTheDocument();
    });

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/users/profile",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          fullName: "Updated Name",
          birthDate: "1990-01-01",
          email: "test@example.com",
        }),
      })
    );

    expect(apiFetch).toHaveBeenCalledWith(
      "/api/v1/users/settings",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          sharePresence: false,
        }),
      })
    );
  });

  it("handles settings or profile load errors gracefully", async () => {
    (apiFetch as jest.Mock).mockRejectedValueOnce(new Error("Failed to load"));

    render(<MyProfileCard onBack={jest.fn()} />);

    await waitFor(() => {
      expect(
        screen.getByText(/failed to load profile details/i)
      ).toBeInTheDocument();
    });
  });
});
