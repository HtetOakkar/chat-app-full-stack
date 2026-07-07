import React from "react";
import { render, act, screen, renderHook } from "@testing-library/react";
import { CallProvider, useCall } from "./CallContext";
import { useConnection } from "./ConnectionContext";
import { useAuth } from "./AuthContext";

// Setup mocks
const mockPublish = jest.fn();
let lastSubscribedCallback: any = null;
const mockSubscribe = jest.fn((destination: string, callback: any) => {
  if (destination === "/user/queue/call") {
    lastSubscribedCallback = callback;
  }
  return jest.fn(); // return unsubscribe func
});

jest.mock("./ConnectionContext", () => ({
  useConnection: () => ({
    subscribe: mockSubscribe,
    stompClientRef: {
      current: {
        connected: true,
        publish: mockPublish,
      },
    },
  }),
}));

jest.mock("./AuthContext", () => ({
  useAuth: () => ({
    userId: 1,
    token: "mock-token",
    isAuthenticated: true,
  }),
}));

// Mock WebRTC APIs
class MockRTCPeerConnection {
  static latestInstance: any = null;
  onicecandidate = null;
  ontrack = null;
  oniceconnectionstatechange: any = null;
  onconnectionstatechange: any = null;
  iceConnectionState = "new";
  connectionState = "new";
  remoteDescription: any = null;
  transceivers: any[] = [];
  senders: any[] = [];
  constructor() {
    MockRTCPeerConnection.latestInstance = this;
  }
  getTransceivers() {
    return this.transceivers;
  }
  getSenders() {
    return this.senders;
  }
  addTransceiver(trackOrKind: any, init?: any) {
    const sender = {
      track:
        typeof trackOrKind === "string" ? { kind: trackOrKind } : trackOrKind,
      replaceTrack: jest.fn().mockResolvedValue(undefined),
      getParameters: jest.fn().mockReturnValue({ encodings: [{}] }),
      setParameters: jest.fn().mockResolvedValue(undefined),
    };
    const t = {
      sender,
      receiver: {
        track:
          typeof trackOrKind === "string" ? { kind: trackOrKind } : trackOrKind,
      },
      direction: init?.direction || "sendrecv",
    };
    this.transceivers.push(t);
    this.senders.push(sender);
    return t;
  }
  createOffer(options?: any) {
    return Promise.resolve({
      sdp: options?.iceRestart ? "ice-restart-offer-sdp" : "offer-sdp",
    });
  }
  createAnswer() {
    return Promise.resolve({ sdp: "answer-sdp" });
  }
  setLocalDescription() {
    return Promise.resolve();
  }
  setRemoteDescription(desc: any) {
    this.remoteDescription = desc;
    return Promise.resolve();
  }
  addIceCandidate(cand: any) {
    return Promise.resolve();
  }
  addTrack(track: any) {
    const sender = {
      track,
      replaceTrack: jest.fn().mockResolvedValue(undefined),
      getParameters: jest.fn().mockReturnValue({ encodings: [{}] }),
      setParameters: jest.fn().mockResolvedValue(undefined),
    };
    this.senders.push(sender);
    return sender;
  }
  close() {}
}

const mockStop = jest.fn();
const mockGetUserMedia = jest.fn().mockImplementation((constraints?: any) => {
  const hasAudio = !constraints || constraints.audio !== false;
  const hasVideo = constraints && constraints.video;
  const tracks: any[] = [];
  if (hasAudio) {
    tracks.push({ stop: mockStop, enabled: true, kind: "audio" });
  }
  if (hasVideo) {
    tracks.push({ stop: mockStop, enabled: true, kind: "video" });
  }
  return Promise.resolve({
    getTracks: () => tracks,
    getAudioTracks: () => tracks.filter((t) => t.kind === "audio"),
    getVideoTracks: () => tracks.filter((t) => t.kind === "video"),
    addTrack: (track: any) => tracks.push(track),
  });
});

Object.defineProperty(global, "RTCPeerConnection", {
  value: MockRTCPeerConnection,
  writable: true,
});

class MockRTCSessionDescription {
  type: string;
  sdp: string;
  constructor(init?: any) {
    this.type = init?.type || "";
    this.sdp = init?.sdp || "";
  }
}
Object.defineProperty(global, "RTCSessionDescription", {
  value: MockRTCSessionDescription,
  writable: true,
});

class MockRTCIceCandidate {
  candidate: string;
  constructor(init?: any) {
    this.candidate = init?.candidate || "";
  }
}
Object.defineProperty(global, "RTCIceCandidate", {
  value: MockRTCIceCandidate,
  writable: true,
});

class MockMediaStream {
  tracks: any[];
  constructor(tracks?: any[]) {
    this.tracks = tracks || [];
  }
  getTracks() {
    return this.tracks;
  }
  getAudioTracks() {
    return this.tracks.filter((t) => t.kind === "audio");
  }
  getVideoTracks() {
    return this.tracks.filter((t) => t.kind === "video");
  }
  addTrack(track: any) {
    this.tracks.push(track);
  }
  removeTrack(track: any) {
    this.tracks = this.tracks.filter((t) => t !== track && t.id !== track.id);
  }
  clone() {
    return new MockMediaStream([...this.tracks]);
  }
}
Object.defineProperty(global, "MediaStream", {
  value: MockMediaStream,
  writable: true,
});
if (typeof window !== "undefined") {
  (window as any).MediaStream = MockMediaStream;
}

class MockAudioContext {
  state = "suspended";
  createOscillator() {
    return {
      frequency: { value: 0 },
      connect: jest.fn(),
      start: jest.fn(),
      stop: jest.fn(),
    };
  }
  createGain() {
    return {
      gain: {
        value: 0,
        setValueAtTime: jest.fn(),
        exponentialRampToValueAtTime: jest.fn(),
      },
      connect: jest.fn(),
      disconnect: jest.fn(),
    };
  }
  resume() {
    this.state = "running";
    return Promise.resolve();
  }
  destination = {};
  currentTime = 0;
}

if (typeof window !== "undefined") {
  (window as any).AudioContext = MockAudioContext;
  (window as any).webkitAudioContext = MockAudioContext;
  Object.defineProperty(navigator, "mediaDevices", {
    value: {
      getUserMedia: mockGetUserMedia,
    },
    writable: true,
    configurable: true,
  });
}

describe("CallContext State Machine", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    lastSubscribedCallback = null;
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CallProvider>{children}</CallProvider>
  );

  test("initializes with idle state", () => {
    const { result } = renderHook(() => useCall(), { wrapper });
    expect(result.current.status).toBe("idle");
    expect(result.current.localStream).toBeNull();
    expect(result.current.remoteStream).toBeNull();
  });

  test("transition to dialing when initiating a call", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
    });

    expect(result.current.status).toBe("dialing");
    expect(result.current.callType).toBe("VIDEO");
    expect(result.current.callerInfo).toEqual({ id: 2, fullName: "User Two" });
    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({
        destination: "/app/call.offer",
      })
    );
  });

  test("transition to ringing when receiving an offer signal", () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    expect(lastSubscribedCallback).not.toBeNull();

    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "AUDIO",
          sdp: "offer-sdp-incoming",
        }),
      });
    });

    expect(result.current.status).toBe("ringing");
    expect(result.current.callType).toBe("AUDIO");
    expect(result.current.callerInfo).toEqual({
      id: 3,
      sdp: "offer-sdp-incoming",
    });
  });

  test("populates callerInfo with fullName and username when receiving an offer signal", () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    expect(lastSubscribedCallback).not.toBeNull();

    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 42,
          type: "offer",
          callType: "VIDEO",
          sdp: "offer-sdp-42",
          senderFullName: "Alice Wonder",
          senderUsername: "alicew",
        }),
      });
    });

    expect(result.current.status).toBe("ringing");
    expect(result.current.callerInfo).toEqual({
      id: 42,
      sdp: "offer-sdp-42",
      fullName: "Alice Wonder",
      username: "alicew",
    });
  });

  test("transition to active when callee accepts the call", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Receive incoming call offer
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "AUDIO",
          sdp: "offer-sdp-incoming",
        }),
      });
    });

    // 2. Accept call
    await act(async () => {
      await result.current.acceptCall();
    });

    expect(result.current.status).toBe("active");
    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({
        destination: "/app/call.answer",
      })
    );
  });

  test("transition to ended and clears states on hangup", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // Initiate call
    await act(async () => {
      await result.current.initiateCall(2, "AUDIO", "User Two");
    });

    expect(result.current.status).toBe("dialing");

    // Hangup
    act(() => {
      result.current.hangupCall();
    });

    expect(result.current.status).toBe("ended");
    expect(result.current.localStream).toBeNull();
    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({
        destination: "/app/call.hangup",
      })
    );
  });

  test("handles missing mediaDevices gracefully on initiateCall", async () => {
    const originalMediaDevices = navigator.mediaDevices;
    Object.defineProperty(navigator, "mediaDevices", {
      value: undefined,
      configurable: true,
      writable: true,
    });

    const spyAlert = jest.spyOn(window, "alert").mockImplementation(() => {});

    const { result } = renderHook(() => useCall(), { wrapper });

    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
    });

    expect(result.current.status).toBe("ended");
    expect(spyAlert).toHaveBeenCalled();

    spyAlert.mockRestore();
    Object.defineProperty(navigator, "mediaDevices", {
      value: originalMediaDevices,
      configurable: true,
      writable: true,
    });
  });

  test("queues ICE candidates when remote description is not set", async () => {
    const spyAddIceCandidate = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "addIceCandidate"
    );
    const { result } = renderHook(() => useCall(), { wrapper });

    // Initiate call (peer connection created, remoteDescription is null)
    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
    });

    // Receive ICE candidate signal before answer/remoteDescription is set
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "ice",
          candidate: { candidate: "candidate-data" },
        }),
      });
    });

    // It should not be added yet
    expect(spyAddIceCandidate).not.toHaveBeenCalled();
    spyAddIceCandidate.mockRestore();
  });

  test("flushes and applies queued ICE candidates after remote description is set", async () => {
    const spyAddIceCandidate = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "addIceCandidate"
    );
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Receive incoming call offer (rings, pc is not created yet)
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "VIDEO",
          sdp: "offer-sdp-incoming",
        }),
      });
    });

    // 2. Receive early ICE candidate while ringing
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "ice",
          candidate: { candidate: "candidate-data-1" },
        }),
      });
    });

    expect(spyAddIceCandidate).not.toHaveBeenCalled();

    // 3. Accept call (setRemoteDescription will be called)
    await act(async () => {
      await result.current.acceptCall();
    });

    // Queued candidate should be applied
    expect(spyAddIceCandidate).toHaveBeenCalledWith(
      expect.objectContaining({ candidate: "candidate-data-1" })
    );
    spyAddIceCandidate.mockRestore();
  });

  test("transitions to active when answer signal is received after initiating call", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Initiate call (status becomes "dialing")
    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
    });
    expect(result.current.status).toBe("dialing");

    // 2. Receive answer signal from callee
    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "answer",
          sdp: "answer-sdp-from-callee",
        }),
      });
    });

    // Should transition to active — this would fail with stale closure
    expect(result.current.status).toBe("active");
  });

  test("transitions to ended when hangup signal is received during active call", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Receive incoming offer
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "AUDIO",
          sdp: "offer-sdp",
        }),
      });
    });
    expect(result.current.status).toBe("ringing");

    // 2. Accept call
    await act(async () => {
      await result.current.acceptCall();
    });
    expect(result.current.status).toBe("active");

    // 3. Remote side hangs up
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "hangup",
        }),
      });
    });

    // Should transition to ended — this would fail with stale closure
    expect(result.current.status).toBe("ended");
  });

  test("subscribes to call queue only once and does not re-subscribe on state changes", async () => {
    mockSubscribe.mockClear();
    const { result } = renderHook(() => useCall(), { wrapper });

    const initialCallCount = mockSubscribe.mock.calls.filter(
      ([dest]: [string, any]) => dest === "/user/queue/call"
    ).length;

    // Initiate a call (causes status change: idle -> dialing)
    await act(async () => {
      await result.current.initiateCall(2, "AUDIO", "User Two");
    });

    // Receive answer (causes status change: dialing -> active)
    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "answer",
          sdp: "answer-sdp",
        }),
      });
    });

    const finalCallCount = mockSubscribe.mock.calls.filter(
      ([dest]: [string, any]) => dest === "/user/queue/call"
    ).length;

    // Should NOT have re-subscribed
    expect(finalCallCount).toBe(initialCallCount);
  });

  test("supports full call lifecycle and can receive a new call after previous call ended", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Receive offer for first call
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "AUDIO",
          sdp: "offer-sdp",
        }),
      });
    });
    expect(result.current.status).toBe("ringing");
    expect(result.current.callerInfo?.id).toBe(2);

    // 2. Accept call
    await act(async () => {
      await result.current.acceptCall();
    });
    expect(result.current.status).toBe("active");

    // 3. Hangup
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "hangup",
        }),
      });
    });
    expect(result.current.status).toBe("ended");

    // 4. Receive offer for second call after first call ended
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "VIDEO",
          sdp: "offer-sdp-3",
        }),
      });
    });
    expect(result.current.status).toBe("ringing");
    expect(result.current.callerInfo?.id).toBe(3);
    expect(result.current.callType).toBe("VIDEO");
  });

  it("uses ServiceWorker fallback for incoming call notifications on mobile devices when document is hidden", async () => {
    const mockShowNotification = jest.fn().mockResolvedValue(undefined);
    const originalNavigator = global.navigator;
    const originalNotification = global.Notification;
    const originalHidden = document.hidden;

    Object.defineProperty(document, "hidden", {
      value: true,
      writable: true,
      configurable: true,
    });

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

    try {
      const { result } = renderHook(() => useCall(), { wrapper: CallProvider });

      await act(async () => {
        lastSubscribedCallback({
          body: JSON.stringify({
            senderId: 4,
            type: "offer",
            callType: "AUDIO",
            sdp: "offer-sdp-4",
          }),
        });
      });

      expect(mockShowNotification).toHaveBeenCalledWith("Incoming Call", {
        body: "Incoming AUDIO call from Contact",
      });
    } finally {
      Object.defineProperty(document, "hidden", {
        value: originalHidden,
        writable: true,
        configurable: true,
      });
      Object.defineProperty(global, "navigator", {
        value: originalNavigator,
        writable: true,
        configurable: true,
      });
      Object.defineProperty(global, "Notification", {
        value: originalNotification,
        writable: true,
        configurable: true,
      });
    }
  });
});

describe("CallContext Audio Output Routing", () => {
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CallProvider>{children}</CallProvider>
  );

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("enumerates audio output devices, classifies them, and excludes earpiece during VIDEO calls", async () => {
    const mockDevices = [
      {
        deviceId: "bt-1",
        label: "AirPods Bluetooth Headset",
        kind: "audiooutput",
      },
      { deviceId: "spk-1", label: "Speakerphone Main", kind: "audiooutput" },
      { deviceId: "ear-1", label: "Phone Earpiece", kind: "audiooutput" },
      { deviceId: "mic-1", label: "Built-in Mic", kind: "audioinput" },
    ];

    const mockEnumerateDevices = jest.fn().mockResolvedValue(mockDevices);
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        ...navigator.mediaDevices,
        enumerateDevices: mockEnumerateDevices,
      },
      writable: true,
      configurable: true,
    });

    const { result } = renderHook(() => useCall(), { wrapper });

    // Wait for device enumeration
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    // In idle / AUDIO mode, earpiece is included
    expect(result.current.audioOutputDevices).toEqual([
      {
        deviceId: "bt-1",
        label: "AirPods Bluetooth Headset",
        type: "bluetooth",
      },
      { deviceId: "spk-1", label: "Speakerphone Main", type: "speaker" },
      { deviceId: "ear-1", label: "Phone Earpiece", type: "earpiece" },
    ]);

    // When initiating a VIDEO call, earpiece must be filtered out
    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
    });

    expect(result.current.audioOutputDevices).toEqual([
      {
        deviceId: "bt-1",
        label: "AirPods Bluetooth Headset",
        type: "bluetooth",
      },
      { deviceId: "spk-1", label: "Speakerphone Main", type: "speaker" },
    ]);
  });

  test("automatically prioritizes bluetooth/headphones first, then falls back to speaker for VIDEO and earpiece for AUDIO", async () => {
    // 1. When Bluetooth/Headphones are present, they take priority
    const devicesWithBt = [
      { deviceId: "spk-1", label: "Speakerphone Main", kind: "audiooutput" },
      {
        deviceId: "bt-1",
        label: "AirPods Bluetooth Headset",
        kind: "audiooutput",
      },
      { deviceId: "ear-1", label: "Phone Earpiece", kind: "audiooutput" },
    ];
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        ...navigator.mediaDevices,
        enumerateDevices: jest.fn().mockResolvedValue(devicesWithBt),
      },
      writable: true,
      configurable: true,
    });

    const { result, rerender } = renderHook(() => useCall(), { wrapper });
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(result.current.selectedAudioOutputId).toBe("bt-1");

    // 2. When no Bluetooth/Headphones, VIDEO calls fall back to speaker
    const devicesNoBt = [
      { deviceId: "spk-1", label: "Speakerphone Main", kind: "audiooutput" },
      { deviceId: "ear-1", label: "Phone Earpiece", kind: "audiooutput" },
    ];
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        ...navigator.mediaDevices,
        enumerateDevices: jest.fn().mockResolvedValue(devicesNoBt),
      },
      writable: true,
      configurable: true,
    });

    await act(async () => {
      await result.current.initiateCall(2, "VIDEO", "User Two");
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(result.current.selectedAudioOutputId).toBe("spk-1");

    // 3. When no Bluetooth/Headphones, AUDIO calls fall back to earpiece
    await act(async () => {
      await result.current.initiateCall(3, "AUDIO", "User Three");
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(result.current.selectedAudioOutputId).toBe("ear-1");
  });

  test("allows manual switching of audio output source and overrides default priority", async () => {
    const mockDevices = [
      { deviceId: "spk-1", label: "Speakerphone Main", kind: "audiooutput" },
      {
        deviceId: "bt-1",
        label: "AirPods Bluetooth Headset",
        kind: "audiooutput",
      },
      { deviceId: "ear-1", label: "Phone Earpiece", kind: "audiooutput" },
    ];
    Object.defineProperty(navigator, "mediaDevices", {
      value: {
        ...navigator.mediaDevices,
        enumerateDevices: jest.fn().mockResolvedValue(mockDevices),
      },
      writable: true,
      configurable: true,
    });

    const { result } = renderHook(() => useCall(), { wrapper });
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    // Automatically selected bt-1
    expect(result.current.selectedAudioOutputId).toBe("bt-1");

    // Manually switch to speaker
    await act(async () => {
      await result.current.switchAudioOutput("spk-1");
    });

    expect(result.current.selectedAudioOutputId).toBe("spk-1");

    // Even if devicechange event fires or re-render happens, it stays on manual selection
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(result.current.selectedAudioOutputId).toBe("spk-1");
  });
});

describe("CallContext Mid-Call Video Upgrade & Renegotiation", () => {
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <CallProvider>{children}</CallProvider>
  );

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("handles in-call renegotiation offer when upgrading from AUDIO to VIDEO", async () => {
    const setRemoteDescriptionSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "setRemoteDescription"
    );
    const createAnswerSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "createAnswer"
    );
    const setLocalDescriptionSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "setLocalDescription"
    );

    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Establish an active AUDIO call
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "AUDIO",
          sdp: "initial-audio-offer",
        }),
      });
    });

    await act(async () => {
      await result.current.acceptCall();
    });

    expect(result.current.status).toBe("active");
    expect(result.current.callType).toBe("AUDIO");
    mockPublish.mockClear();

    // 2. Receive mid-call renegotiation offer to upgrade to VIDEO
    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "offer",
          callType: "VIDEO",
          sdp: "upgrade-video-offer",
        }),
      });
      await new Promise((r) => setTimeout(r, 50));
    });

    // 3. Verify peer connection handled the renegotiation offer and sent answer
    expect(result.current.callType).toBe("VIDEO");
    expect(setRemoteDescriptionSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: "offer", sdp: "upgrade-video-offer" })
    );
    expect(createAnswerSpy).toHaveBeenCalled();
    expect(setLocalDescriptionSpy).toHaveBeenCalled();
    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({
        destination: "/app/call.answer",
      })
    );
  });

  test("handles in-call renegotiation answer when upgrading camera mid-call", async () => {
    const setRemoteDescriptionSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "setRemoteDescription"
    );
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Establish an active AUDIO call
    await act(async () => {
      await result.current.initiateCall(3, "AUDIO");
    });

    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "answer",
          sdp: "initial-audio-answer",
        }),
      });
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(result.current.status).toBe("active");
    expect(result.current.callType).toBe("AUDIO");
    setRemoteDescriptionSpy.mockClear();

    // 2. Initiate mid-call camera upgrade
    await act(async () => {
      await result.current.toggleCamera();
    });
    expect(result.current.callType).toBe("VIDEO");

    // 3. Receive renegotiation answer from callee
    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 3,
          type: "answer",
          sdp: "upgrade-video-answer",
        }),
      });
      await new Promise((r) => setTimeout(r, 50));
    });

    // 4. Verify remote description was set for the renegotiation answer
    expect(setRemoteDescriptionSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: "answer", sdp: "upgrade-video-answer" })
    );
    expect(result.current.status).toBe("active");
  });

  test("handles in-call renegotiation offer even when STOMP senderId is string and callerInfo id is number", async () => {
    const setRemoteDescriptionSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "setRemoteDescription"
    );
    const createAnswerSpy = jest.spyOn(
      MockRTCPeerConnection.prototype,
      "createAnswer"
    );

    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Establish an active AUDIO call where callerInfo id is a number (2)
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "AUDIO",
          sdp: "fake-offer-sdp",
        }),
      });
    });
    await act(async () => {
      await result.current.acceptCall();
    });
    expect(result.current.status).toBe("active");
    expect(result.current.callType).toBe("AUDIO");

    // 2. Receive in-call renegotiation offer from senderId as STRING "2"
    await act(async () => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: "2", // string ID from STOMP
          type: "offer",
          callType: "VIDEO",
          sdp: "renegotiate-offer-sdp",
        }),
      });
      await new Promise((r) => setTimeout(r, 50));
    });

    // 3. Verify renegotiation offer was processed
    expect(setRemoteDescriptionSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: "offer", sdp: "renegotiate-offer-sdp" })
    );
    expect(createAnswerSpy).toHaveBeenCalled();
    expect(result.current.callType).toBe("VIDEO");
  });

  test("upgrades transceiver direction to sendrecv and replaces sender track when turning on camera mid-call with existing transceiver", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    // 1. Establish an active AUDIO call
    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "AUDIO",
          sdp: "fake-offer-sdp",
        }),
      });
    });
    await act(async () => {
      await result.current.acceptCall();
    });
    expect(result.current.status).toBe("active");

    // 2. Simulate existing recvonly transceiver (e.g. from remote peer turning on camera first)
    const pc =
      (result.current as any)._pcRef?.current ||
      (MockRTCPeerConnection.prototype as any);
    // Since we can't easily access private ref, let's inspect the active mock instance if possible or inject via method
    // In our test suite, we can check how pcRef is accessed or let's check what toggleCamera does
    // When acceptCall runs, a MockRTCPeerConnection is created. Let's spy on getTransceivers.
    const mockReplaceTrack = jest.fn().mockResolvedValue(undefined);
    const mockTransceiver = {
      sender: { track: null, replaceTrack: mockReplaceTrack },
      receiver: { track: { kind: "video" } },
      direction: "recvonly",
    };
    const getTransceiversSpy = jest
      .spyOn(MockRTCPeerConnection.prototype, "getTransceivers")
      .mockReturnValue([mockTransceiver]);

    // 3. Initiate mid-call camera upgrade
    await act(async () => {
      await result.current.toggleCamera();
    });

    // 4. Verify transceiver direction was upgraded to sendrecv and replaceTrack called
    expect(getTransceiversSpy).toHaveBeenCalled();
    expect(mockReplaceTrack).toHaveBeenCalled();
    expect(mockTransceiver.direction).toBe("sendrecv");

    getTransceiversSpy.mockRestore();
  });

  test("ensures ontrack explicitly merges event.track with existing stream tracks so video is not omitted when browser stream is stale", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "AUDIO",
          sdp: "fake-offer-sdp",
        }),
      });
    });
    await act(async () => {
      await result.current.acceptCall();
    });

    const pc = MockRTCPeerConnection.latestInstance;
    expect(pc).toBeDefined();
    expect(pc.ontrack).toBeDefined();

    // Simulate ontrack where event.streams[0] has ONLY an audio track (stale stream), but event.track is a new video track
    const audioTrack = { kind: "audio", id: "aud-1", stop: jest.fn() };
    const videoTrack = { kind: "video", id: "vid-1", stop: jest.fn() };
    const staleStream = { getTracks: () => [audioTrack] };

    act(() => {
      pc.ontrack({ track: videoTrack, streams: [staleStream] });
    });

    // Without our fix, remoteStream will only contain [audioTrack] because it took new MediaStream(event.streams[0].getTracks()).
    // With our fix, it must contain both [audioTrack, videoTrack].
    const tracks = result.current.remoteStream
      ? result.current.remoteStream.getTracks()
      : [];
    expect(
      tracks.some((t: any) => t.id === "vid-1" && t.kind === "video")
    ).toBe(true);
    expect(
      tracks.some((t: any) => t.id === "aud-1" && t.kind === "audio")
    ).toBe(true);
  });

  test("filters out ended remote tracks in ontrack and removes them when track fires ended event so video does not freeze", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "VIDEO",
          sdp: "fake-offer-sdp",
        }),
      });
    });
    await act(async () => {
      await result.current.acceptCall();
    });

    const pc = MockRTCPeerConnection.latestInstance;
    expect(pc).toBeDefined();
    expect(pc.ontrack).toBeDefined();

    let endedHandler: (() => void) | undefined;
    const oldEndedVideoTrack = {
      kind: "video",
      id: "vid-old",
      readyState: "ended",
      stop: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    };
    const activeVideoTrack = {
      kind: "video",
      id: "vid-active",
      readyState: "live",
      stop: jest.fn(),
      addEventListener: jest.fn((event: string, handler: any) => {
        if (event === "ended") endedHandler = handler;
      }),
      removeEventListener: jest.fn(),
    };
    const audioTrack = {
      kind: "audio",
      id: "aud-1",
      readyState: "live",
      stop: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    };

    const stream = {
      getTracks: () => [oldEndedVideoTrack, activeVideoTrack, audioTrack],
    };

    act(() => {
      pc.ontrack({ track: activeVideoTrack, streams: [stream] });
    });

    let tracks = result.current.remoteStream
      ? result.current.remoteStream.getTracks()
      : [];
    expect(tracks.some((t: any) => t.id === "vid-old")).toBe(false);
    expect(tracks.find((t: any) => t.kind === "video")?.id).toBe("vid-active");

    expect(endedHandler).toBeDefined();
    act(() => {
      activeVideoTrack.readyState = "ended";
      if (endedHandler) endedHandler();
    });

    tracks = result.current.remoteStream
      ? result.current.remoteStream.getTracks()
      : [];
    expect(tracks.some((t: any) => t.id === "vid-active")).toBe(false);
  });

  test("automatically attempts ICE restart when iceConnectionState transitions to failed during an active call", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    act(() => {
      lastSubscribedCallback({
        body: JSON.stringify({
          senderId: 2,
          type: "offer",
          callType: "VIDEO",
          sdp: "fake-offer-sdp",
        }),
      });
    });
    await act(async () => {
      await result.current.acceptCall();
    });

    const pc = MockRTCPeerConnection.latestInstance;
    expect(pc).toBeDefined();
    expect(pc.oniceconnectionstatechange).toBeDefined();

    mockPublish.mockClear();

    await act(async () => {
      pc.iceConnectionState = "failed";
      pc.oniceconnectionstatechange();
    });

    expect(mockPublish).toHaveBeenCalledWith(
      expect.objectContaining({
        destination: "/app/call.offer",
        body: expect.stringContaining("ice-restart-offer-sdp"),
      })
    );
  });

  test("configures video RTCRtpSender encoding parameters with maxBitrate and networkPriority during video call initiation to prevent network congestion freezes", async () => {
    const { result } = renderHook(() => useCall(), { wrapper });

    await act(async () => {
      await result.current.initiateCall(2, "VIDEO");
    });

    const pc = MockRTCPeerConnection.latestInstance;
    expect(pc).toBeDefined();

    const videoSender = pc
      .getSenders()
      .find((s: any) => s.track && s.track.kind === "video");
    expect(videoSender).toBeDefined();
    expect(videoSender.setParameters).toHaveBeenCalledWith(
      expect.objectContaining({
        encodings: expect.arrayContaining([
          expect.objectContaining({
            maxBitrate: 1500000,
            networkPriority: "high",
          }),
        ]),
      })
    );
  });
});
