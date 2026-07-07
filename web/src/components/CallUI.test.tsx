import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import IncomingCallModal from "./IncomingCallModal";
import ActiveCallOverlay from "./ActiveCallOverlay";
import { useCall } from "@/context/CallContext";

jest.mock("@/context/CallContext", () => ({
  useCall: jest.fn(),
}));

describe("IncomingCallModal", () => {
  const mockAcceptCall = jest.fn();
  const mockRejectCall = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders nothing when status is idle", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "idle",
      callerInfo: null,
      callType: null,
      acceptCall: mockAcceptCall,
      rejectCall: mockRejectCall,
    });

    const { container } = render(<IncomingCallModal />);
    expect(container.firstChild).toBeNull();
  });

  test("renders modal when status is ringing", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: { id: 3, fullName: "Alice" },
      callType: "VIDEO",
      acceptCall: mockAcceptCall,
      rejectCall: mockRejectCall,
    });

    render(<IncomingCallModal />);

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Incoming Video Call...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /accept/i })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /decline/i })
    ).toBeInTheDocument();
  });

  test("displays username when fullName is not available", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: { id: 5, username: "john_doe" },
      callType: "AUDIO",
      acceptCall: mockAcceptCall,
      rejectCall: mockRejectCall,
    });

    render(<IncomingCallModal />);
    expect(screen.getByText("john_doe")).toBeInTheDocument();
  });

  test("displays User ID when neither fullName nor username is available", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: { id: 5 },
      callType: "AUDIO",
      acceptCall: mockAcceptCall,
      rejectCall: mockRejectCall,
    });

    render(<IncomingCallModal />);
    expect(screen.getByText("User 5")).toBeInTheDocument();
  });

  test("clicks accept and decline triggers context methods", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: { id: 3, fullName: "Alice" },
      callType: "VIDEO",
      acceptCall: mockAcceptCall,
      rejectCall: mockRejectCall,
    });

    render(<IncomingCallModal />);

    fireEvent.click(screen.getByRole("button", { name: /accept/i }));
    expect(mockAcceptCall).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: /decline/i }));
    expect(mockRejectCall).toHaveBeenCalledTimes(1);
  });
});

describe("ActiveCallOverlay", () => {
  const mockCancelCall = jest.fn();
  const mockHangupCall = jest.fn();
  const mockToggleMute = jest.fn();
  const mockToggleCamera = jest.fn();
  const mockSetIsFullscreen = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders nothing when status is idle or ringing", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "ringing",
      callerInfo: null,
      localStream: null,
      remoteStream: null,
      isMuted: false,
      isCameraOff: false,
      callType: null,
      isFullscreen: false,
      duration: 0,
    });

    const { container } = render(<ActiveCallOverlay />);
    expect(container.firstChild).toBeNull();
  });

  test("renders overlay on dialing status", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "dialing",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: {},
      remoteStream: null,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 0,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    render(<ActiveCallOverlay />);

    expect(screen.getAllByText("Bob")[0]).toBeInTheDocument();
    expect(screen.getByText("Calling...")).toBeInTheDocument();
    expect(screen.getByTitle("End Call")).toBeInTheDocument();
  });

  test("renders video streams on active video call status", () => {
    const mockLocalStream = { getTracks: () => [] };
    const mockRemoteStream = { getTracks: () => [] };

    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: mockLocalStream,
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 75, // 01:15
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    render(<ActiveCallOverlay />);

    expect(screen.getByText("01:15")).toBeInTheDocument();
    const remoteVideo = screen.getByTestId("remote-video") as HTMLVideoElement;
    const localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(remoteVideo).toBeInTheDocument();
    expect(localVideo).toBeInTheDocument();
    expect(remoteVideo.srcObject).toBe(mockRemoteStream);
    expect(localVideo.srcObject).toBe(mockLocalStream);
  });

  test("maintains original aspect ratio for video streams using object-contain without cropping", () => {
    const mockLocalStream = { getTracks: () => [] };
    const mockRemoteStream = { getTracks: () => [] };

    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: mockLocalStream,
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 10,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    render(<ActiveCallOverlay />);

    const remoteVideo = screen.getByTestId("remote-video") as HTMLVideoElement;
    const localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(remoteVideo).toHaveClass("object-contain");
    expect(remoteVideo).not.toHaveClass("object-cover");
    expect(localVideo).toHaveClass("object-contain");
    expect(localVideo).not.toHaveClass("object-cover");
  });

  test("binds video streams correctly when call transitions from connecting to active", () => {
    const mockLocalStream = { id: "local-stream-id" };
    const mockRemoteStream = { id: "remote-stream-id" };

    let currentCallState: any = {
      status: "connecting",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: mockLocalStream,
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 0,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    };

    (useCall as jest.Mock).mockImplementation(() => currentCallState);

    const { rerender } = render(<ActiveCallOverlay />);

    // In connecting status, video elements are not in the DOM
    expect(screen.queryByTestId("remote-video")).toBeNull();
    expect(screen.queryByTestId("local-video")).toBeNull();

    // Transition to active
    currentCallState = {
      ...currentCallState,
      status: "active",
      duration: 1,
    };

    rerender(<ActiveCallOverlay />);

    // Now video elements should be in the DOM and streams bound
    const remoteVideo = screen.getByTestId("remote-video") as HTMLVideoElement;
    const localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(remoteVideo).toBeInTheDocument();
    expect(localVideo).toBeInTheDocument();
    expect(remoteVideo.srcObject).toBe(mockRemoteStream);
    expect(localVideo.srcObject).toBe(mockLocalStream);
  });

  test("renders and binds remote audio stream on active audio call status", () => {
    const mockRemoteStream = { id: "remote-stream" };

    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: null,
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "AUDIO",
      isFullscreen: false,
      duration: 10,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    render(<ActiveCallOverlay />);

    const remoteAudio = screen.getByTestId("remote-audio") as HTMLAudioElement;
    expect(remoteAudio).toBeInTheDocument();
    expect(remoteAudio.srcObject).toBe(mockRemoteStream);
  });

  test("clicking controls triggers context callbacks", () => {
    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: null,
      remoteStream: null,
      isMuted: false,
      isCameraOff: false,
      callType: "AUDIO",
      isFullscreen: false,
      duration: 10,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    render(<ActiveCallOverlay />);

    fireEvent.click(screen.getByTitle("Mute Microphone"));
    expect(mockToggleMute).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTitle("Turn Camera On"));
    expect(mockToggleCamera).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTitle("Enter Fullscreen"));
    expect(mockSetIsFullscreen).toHaveBeenCalledWith(true);

    fireEvent.click(screen.getByTitle("End Call"));
    expect(mockHangupCall).toHaveBeenCalledTimes(1);
  });

  test("rebinds localStream to local-video element when camera is toggled off and back on", () => {
    const mockLocalStream = { id: "local-stream-id" };
    const mockRemoteStream = { id: "remote-stream-id" };

    let currentCallState: any = {
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: mockLocalStream,
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 10,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    };

    (useCall as jest.Mock).mockImplementation(() => currentCallState);

    const { rerender } = render(<ActiveCallOverlay />);

    let localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(localVideo).toBeInTheDocument();
    expect(localVideo.srcObject).toBe(mockLocalStream);

    // Toggle camera OFF
    currentCallState = {
      ...currentCallState,
      isCameraOff: true,
    };
    rerender(<ActiveCallOverlay />);

    expect(screen.queryByTestId("local-video")).toBeNull();
    expect(screen.getByText("Camera Off")).toBeInTheDocument();

    // Toggle camera back ON
    currentCallState = {
      ...currentCallState,
      isCameraOff: false,
    };
    rerender(<ActiveCallOverlay />);

    localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(localVideo).toBeInTheDocument();
    expect(localVideo.srcObject).toBe(mockLocalStream);
  });

  test("renders audio source switcher button, displays available options on click, and applies setSinkId to remote media element", async () => {
    const mockSwitchAudioOutput = jest.fn();
    const mockRemoteStream = { getTracks: () => [] };
    const mockAudioOutputDevices = [
      { deviceId: "bt-1", label: "AirPods Bluetooth", type: "bluetooth" },
      { deviceId: "spk-1", label: "Speakerphone Main", type: "speaker" },
    ];

    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: { getTracks: () => [] },
      remoteStream: mockRemoteStream,
      isMuted: false,
      isCameraOff: false,
      callType: "VIDEO",
      isFullscreen: false,
      duration: 10,
      audioOutputDevices: mockAudioOutputDevices,
      selectedAudioOutputId: "spk-1",
      switchAudioOutput: mockSwitchAudioOutput,
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    });

    const mockSetSinkId = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(HTMLMediaElement.prototype, "setSinkId", {
      value: mockSetSinkId,
      writable: true,
      configurable: true,
    });

    render(<ActiveCallOverlay />);

    expect(mockSetSinkId).toHaveBeenCalledWith("spk-1");

    const switcherBtn = screen.getByTitle("Switch Audio Source");
    expect(switcherBtn).toBeInTheDocument();
    fireEvent.click(switcherBtn);

    expect(screen.getByText("AirPods Bluetooth")).toBeInTheDocument();
    expect(screen.getByText("Speakerphone Main")).toBeInTheDocument();

    fireEvent.click(screen.getByText("AirPods Bluetooth"));
    expect(mockSwitchAudioOutput).toHaveBeenCalledWith("bt-1");
  });

  test("dynamically switches layout and binds video streams when active call upgrades from AUDIO to VIDEO", () => {
    const mockRemoteStream = { id: "remote-stream" };
    const mockLocalStream = { id: "local-stream" };

    const currentCallState = {
      status: "active" as const,
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: mockLocalStream as any,
      remoteStream: mockRemoteStream as any,
      isMuted: false,
      isCameraOff: false,
      callType: "AUDIO" as const,
      isFullscreen: false,
      duration: 10,
      audioOutputDevices: [],
      selectedAudioOutputId: "",
      switchAudioOutput: jest.fn(),
      cancelCall: mockCancelCall,
      hangupCall: mockHangupCall,
      toggleMute: mockToggleMute,
      toggleCamera: mockToggleCamera,
      setIsFullscreen: mockSetIsFullscreen,
    };

    (useCall as jest.Mock).mockReturnValue(currentCallState);

    const { rerender } = render(<ActiveCallOverlay />);

    // When in AUDIO call, video elements should not be rendered
    expect(screen.queryByTestId("remote-video")).toBeNull();
    expect(screen.queryByTestId("local-video")).toBeNull();
    expect(screen.getByTestId("remote-audio")).toBeInTheDocument();

    // Upgrade to VIDEO call mid-call
    (useCall as jest.Mock).mockReturnValue({
      ...currentCallState,
      callType: "VIDEO",
    });

    const playSpy = jest
      .spyOn(HTMLMediaElement.prototype, "play")
      .mockResolvedValue(undefined);
    rerender(<ActiveCallOverlay />);

    // Now video elements should be in the DOM and streams bound
    const remoteVideo = screen.getByTestId("remote-video") as HTMLVideoElement;
    const localVideo = screen.getByTestId("local-video") as HTMLVideoElement;
    expect(remoteVideo).toBeInTheDocument();
    expect(localVideo).toBeInTheDocument();
    expect(remoteVideo.srcObject).toBe(mockRemoteStream);
    expect(localVideo.srcObject).toBe(mockLocalStream);
    expect(playSpy).toHaveBeenCalled();
    playSpy.mockRestore();
  });

  test("calls play() on remote video element when a remote track fires unmute event to recover playback", async () => {
    const playSpy = jest
      .spyOn(HTMLMediaElement.prototype, "play")
      .mockResolvedValue(undefined);
    const mockTrack = {
      kind: "video",
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    };
    const mockRemoteStream = {
      id: "remote-stream",
      getTracks: () => [mockTrack],
    };

    (useCall as jest.Mock).mockReturnValue({
      status: "active",
      callType: "VIDEO",
      callerInfo: { id: 2, fullName: "Bob" },
      localStream: null,
      remoteStream: mockRemoteStream as any,
      isCameraOff: false,
      isMuted: false,
      selectedAudioOutputId: null,
      switchAudioOutput: jest.fn(),
      initiateCall: jest.fn(),
      acceptCall: jest.fn(),
      rejectCall: jest.fn(),
      cancelCall: jest.fn(),
      hangupCall: jest.fn(),
      toggleMute: jest.fn(),
      toggleCamera: jest.fn(),
      setIsFullscreen: jest.fn(),
    });

    render(<ActiveCallOverlay />);

    expect(mockTrack.addEventListener).toHaveBeenCalledWith(
      "unmute",
      expect.any(Function)
    );

    const unmuteHandler = mockTrack.addEventListener.mock.calls.find(
      (call: any[]) => call[0] === "unmute"
    )?.[1];
    expect(unmuteHandler).toBeDefined();

    playSpy.mockClear();

    unmuteHandler();

    expect(playSpy).toHaveBeenCalled();
    playSpy.mockRestore();
  });
});
