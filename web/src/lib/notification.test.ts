import { showNotification } from "./notification";

describe("showNotification", () => {
  let originalNotification: any;
  let originalNavigator: any;

  beforeEach(() => {
    originalNotification = global.Notification;
    originalNavigator = global.navigator;
  });

  afterEach(() => {
    Object.defineProperty(global, "Notification", {
      value: originalNotification,
      writable: true,
      configurable: true,
    });
    Object.defineProperty(global, "navigator", {
      value: originalNavigator,
      writable: true,
      configurable: true,
    });
    jest.clearAllMocks();
  });

  it("shows a standard notification when permission is granted and constructor succeeds", async () => {
    const mockNotification = jest.fn();
    Object.defineProperty(global, "Notification", {
      value: Object.assign(mockNotification, { permission: "granted" }),
      writable: true,
      configurable: true,
    });

    await showNotification("Test Title", { body: "Test Body" });

    expect(mockNotification).toHaveBeenCalledWith("Test Title", {
      body: "Test Body",
    });
  });

  it("falls back to ServiceWorkerRegistration.showNotification when Notification constructor throws Illegal Constructor", async () => {
    const mockShowNotification = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(global, "navigator", {
      value: {
        serviceWorker: {
          ready: Promise.resolve({
            showNotification: mockShowNotification,
          }),
          getRegistration: jest.fn().mockResolvedValue({
            showNotification: mockShowNotification,
          }),
        },
      },
      writable: true,
      configurable: true,
    });

    const mockNotificationConstructor = jest.fn().mockImplementation(() => {
      throw new TypeError(
        "Failed to construct 'Notification': Illegal constructor. Use ServiceWorkerRegistration.showNotification() instead."
      );
    });
    Object.defineProperty(global, "Notification", {
      value: Object.assign(mockNotificationConstructor, {
        permission: "granted",
      }),
      writable: true,
      configurable: true,
    });

    await showNotification("Incoming Call", { body: "Incoming audio call" });

    expect(mockShowNotification).toHaveBeenCalledWith("Incoming Call", {
      body: "Incoming audio call",
    });
  });

  it("requests permission when not granted or denied, and shows notification if granted", async () => {
    const mockNotification = jest.fn();
    const mockRequestPermission = jest.fn().mockResolvedValue("granted");
    Object.defineProperty(global, "Notification", {
      value: Object.assign(mockNotification, {
        permission: "default",
        requestPermission: mockRequestPermission,
      }),
      writable: true,
      configurable: true,
    });

    await showNotification("Test Title", { body: "Test Body" });

    expect(mockRequestPermission).toHaveBeenCalled();
    expect(mockNotification).toHaveBeenCalledWith("Test Title", {
      body: "Test Body",
    });
  });
});
