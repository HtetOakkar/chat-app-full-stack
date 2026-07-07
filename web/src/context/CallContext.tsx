"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
  useMemo,
} from "react";
import { useConnection } from "./ConnectionContext";
import { useAuth } from "./AuthContext";
import { showNotification } from "../lib/notification";

export type CallStatus =
  | "idle"
  | "dialing"
  | "ringing"
  | "connecting"
  | "active"
  | "ended";
export type CallType = "AUDIO" | "VIDEO";

export interface CallerInfo {
  id: number;
  fullName?: string;
  username?: string;
  sdp?: string;
}

class CallAudioSynth {
  private ctx: AudioContext | null = null;
  private osc1: OscillatorNode | null = null;
  private osc2: OscillatorNode | null = null;
  private gain: GainNode | null = null;
  private intervalId: ReturnType<typeof setInterval> | null = null;

  private init() {
    if (typeof window !== "undefined" && !this.ctx) {
      const AudioContextClass =
        window.AudioContext ||
        (window as Window & { webkitAudioContext?: typeof AudioContext })
          .webkitAudioContext;
      if (AudioContextClass) {
        this.ctx = new AudioContextClass();
      }
    }
  }

  playDialtone() {
    try {
      this.stop();
      this.init();
      if (!this.ctx) return;

      if (this.ctx.state === "suspended") {
        this.ctx.resume();
      }

      this.osc1 = this.ctx.createOscillator();
      this.osc2 = this.ctx.createOscillator();
      this.gain = this.ctx.createGain();

      this.osc1.frequency.value = 350;
      this.osc2.frequency.value = 440;
      this.gain.gain.value = 0.05;

      this.osc1.connect(this.gain);
      this.osc2.connect(this.gain);
      this.gain.connect(this.ctx.destination);

      this.osc1.start();
      this.osc2.start();
    } catch (e) {
      console.warn("Failed to play dialtone:", e);
    }
  }

  playRingtone() {
    try {
      this.stop();
      this.init();
      if (!this.ctx) return;

      if (this.ctx.state === "suspended") {
        this.ctx.resume();
      }

      const playBeep = () => {
        try {
          if (!this.ctx) return;
          const osc = this.ctx.createOscillator();
          const g = this.ctx.createGain();
          osc.frequency.value = 440;
          g.gain.setValueAtTime(0.1, this.ctx.currentTime);
          g.gain.exponentialRampToValueAtTime(
            0.001,
            this.ctx.currentTime + 1.8
          );
          osc.connect(g);
          g.connect(this.ctx.destination);
          osc.start();
          osc.stop(this.ctx.currentTime + 2.0);
        } catch {}
      };

      playBeep();
      this.intervalId = setInterval(playBeep, 3000);
    } catch (e) {
      console.warn("Failed to play ringtone:", e);
    }
  }

  stop() {
    try {
      if (this.intervalId) {
        clearInterval(this.intervalId);
        this.intervalId = null;
      }
      if (this.osc1) {
        this.osc1.stop();
        this.osc1 = null;
      }
      if (this.osc2) {
        this.osc2.stop();
        this.osc2 = null;
      }
      if (this.gain) {
        this.gain.disconnect();
        this.gain = null;
      }
    } catch {}
  }
}

export interface AudioOutputDeviceInfo {
  deviceId: string;
  label: string;
  type: "bluetooth" | "headphones" | "speaker" | "earpiece" | "other";
}

const classifyAudioOutputDevice = (
  label: string
): AudioOutputDeviceInfo["type"] => {
  const l = label.toLowerCase();
  if (/bluetooth|airpods|wireless|bt|buds|headset/.test(l)) return "bluetooth";
  if (/headphone|earphone|wired/.test(l)) return "headphones";
  if (/speaker|speakerphone|main|loudspeaker/.test(l)) return "speaker";
  if (/earpiece|phone|handset|default/.test(l)) return "earpiece";
  return "other";
};

export interface CallContextType {
  status: CallStatus;
  callerInfo: CallerInfo | null;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  isMuted: boolean;
  isCameraOff: boolean;
  callType: CallType | null;
  isFullscreen: boolean;
  duration: number;
  audioOutputDevices: AudioOutputDeviceInfo[];
  selectedAudioOutputId: string | null;
  switchAudioOutput: (deviceId: string) => Promise<void>;
  initiateCall: (
    recipientId: number,
    callType: CallType,
    recipientName?: string
  ) => Promise<void>;
  acceptCall: () => Promise<void>;
  rejectCall: () => void;
  cancelCall: () => void;
  hangupCall: () => void;
  toggleMute: () => void;
  toggleCamera: () => Promise<void>;
  setIsFullscreen: (v: boolean) => void;
}

const CallContext = createContext<CallContextType | null>(null);
const configureVideoSender = (sender: RTCRtpSender | null | undefined) => {
  if (
    !sender ||
    typeof sender.getParameters !== "function" ||
    typeof sender.setParameters !== "function"
  )
    return;
  try {
    const params = sender.getParameters();
    if (!params.encodings || params.encodings.length === 0) {
      params.encodings = [{}];
    }
    params.encodings.forEach((enc: RTCRtpEncodingParameters) => {
      enc.maxBitrate = 1500000;
      enc.networkPriority = "high";
    });
    sender.setParameters(params).catch(() => {});
  } catch (e) {
    console.warn("Failed to set video sender parameters:", e);
  }
};

export const CallProvider = ({ children }: { children: React.ReactNode }) => {
  const { subscribe, stompClientRef } = useConnection();
  const { userId } = useAuth();

  const [status, setStatus] = useState<CallStatus>("idle");
  const [callerInfo, setCallerInfo] = useState<CallerInfo | null>(null);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [isMuted, setIsMuted] = useState(false);
  const [isCameraOff, setIsCameraOff] = useState(false);
  const [callType, setCallType] = useState<CallType | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [duration, setDuration] = useState(0);
  const [allAudioOutputDevices, setAllAudioOutputDevices] = useState<
    AudioOutputDeviceInfo[]
  >([]);
  const [selectedAudioOutputId, setSelectedAudioOutputId] = useState<
    string | null
  >(null);
  const userManuallySelectedRef = useRef(false);

  const refreshAudioDevices = useCallback(async () => {
    if (
      typeof window === "undefined" ||
      !navigator.mediaDevices ||
      !navigator.mediaDevices.enumerateDevices
    )
      return;
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const outputs = devices
        .filter((d) => d.kind === "audiooutput")
        .map((d) => ({
          deviceId: d.deviceId,
          label: d.label || `Speaker (${d.deviceId.slice(0, 4)})`,
          type: classifyAudioOutputDevice(d.label || ""),
        }));
      setAllAudioOutputDevices(outputs);
    } catch (err) {
      console.warn("Failed to enumerate audio devices:", err);
    }
  }, []);

  useEffect(() => {
    // Defer execution to avoid synchronous state update warning in useEffect
    const handle = setTimeout(() => {
      refreshAudioDevices();
    }, 0);
    if (
      typeof window !== "undefined" &&
      navigator.mediaDevices &&
      navigator.mediaDevices.addEventListener
    ) {
      navigator.mediaDevices.addEventListener(
        "devicechange",
        refreshAudioDevices
      );
      return () => {
        clearTimeout(handle);
        navigator.mediaDevices.removeEventListener(
          "devicechange",
          refreshAudioDevices
        );
      };
    }
    return () => clearTimeout(handle);
  }, [refreshAudioDevices]);

  const audioOutputDevices = useMemo(() => {
    if (callType === "VIDEO") {
      return allAudioOutputDevices.filter((d) => d.type !== "earpiece");
    }
    return allAudioOutputDevices;
  }, [allAudioOutputDevices, callType]);

  const switchAudioOutput = useCallback(async (deviceId: string) => {
    userManuallySelectedRef.current = true;
    setSelectedAudioOutputId(deviceId);
  }, []);

  useEffect(() => {
    if (allAudioOutputDevices.length === 0) return;

    if (userManuallySelectedRef.current) {
      const isStillValid =
        selectedAudioOutputId &&
        audioOutputDevices.some((d) => d.deviceId === selectedAudioOutputId);
      if (isStillValid) return;
    }

    // Defer state update to avoid cascading render warning in useEffect
    const handle = setTimeout(() => {
      // 1. Bluetooth / Headphones first
      const btOrHeadphones = allAudioOutputDevices.find(
        (d) => d.type === "bluetooth" || d.type === "headphones"
      );
      if (btOrHeadphones) {
        setSelectedAudioOutputId(btOrHeadphones.deviceId);
        return;
      }

      // 2. Video calls fallback to Speaker
      if (callType === "VIDEO") {
        const speaker = allAudioOutputDevices.find((d) => d.type === "speaker");
        if (speaker) {
          setSelectedAudioOutputId(speaker.deviceId);
          return;
        }
        const anyNonEarpiece = allAudioOutputDevices.find(
          (d) => d.type !== "earpiece"
        );
        if (anyNonEarpiece) {
          setSelectedAudioOutputId(anyNonEarpiece.deviceId);
          return;
        }
      }

      // 3. Audio calls fallback to Earpiece
      const earpiece = allAudioOutputDevices.find((d) => d.type === "earpiece");
      if (earpiece) {
        setSelectedAudioOutputId(earpiece.deviceId);
        return;
      }

      // Fallback to first available device
      if (allAudioOutputDevices[0]) {
        setSelectedAudioOutputId(allAudioOutputDevices[0].deviceId);
      }
    }, 0);

    return () => clearTimeout(handle);
  }, [
    allAudioOutputDevices,
    callType,
    audioOutputDevices,
    selectedAudioOutputId,
  ]);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const audioSynthRef = useRef<CallAudioSynth | null>(null);
  const durationIntervalRef = useRef<ReturnType<typeof setInterval> | null>(
    null
  );
  const unansweredTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(
    null
  );
  const iceQueueRef = useRef<RTCIceCandidateInit[]>([]);

  // Refs to avoid stale closures in the STOMP subscriber
  const statusRef = useRef<CallStatus>(status);
  const callerInfoRef = useRef<CallerInfo | null>(callerInfo);
  const callTypeRef = useRef<CallType | null>(callType);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);
  useEffect(() => {
    callerInfoRef.current = callerInfo;
  }, [callerInfo]);
  useEffect(() => {
    callTypeRef.current = callType;
  }, [callType]);

  // Refs for callbacks used in the STOMP subscriber to avoid re-subscription
  const cleanupRef = useRef<() => void>(() => {});
  const playRingtoneRef = useRef<() => void>(() => {});
  const stopDialtoneRef = useRef<() => void>(() => {});
  const publishSignalRef = useRef<(type: string, data: object) => void>(
    () => {}
  );

  // Initialize Audio Synthesizer
  useEffect(() => {
    if (typeof window !== "undefined") {
      audioSynthRef.current = new CallAudioSynth();
    }
  }, []);

  const playRingtone = useCallback(() => {
    audioSynthRef.current?.playRingtone();
  }, []);

  const stopRingtone = useCallback(() => {
    audioSynthRef.current?.stop();
  }, []);

  const playDialtone = useCallback(() => {
    audioSynthRef.current?.playDialtone();
  }, []);

  const stopDialtone = useCallback(() => {
    audioSynthRef.current?.stop();
  }, []);

  // Cleanup helper to terminate WebRTC media and peer connection
  const cleanupMediaAndConnection = useCallback(() => {
    stopRingtone();
    stopDialtone();
    iceQueueRef.current = [];

    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
      durationIntervalRef.current = null;
    }
    if (unansweredTimeoutRef.current) {
      clearTimeout(unansweredTimeoutRef.current);
      unansweredTimeoutRef.current = null;
    }

    if (pcRef.current) {
      try {
        pcRef.current.close();
      } catch (err) {
        console.error("Error closing peer connection:", err);
      }
      pcRef.current = null;
    }

    if (localStreamRef.current) {
      try {
        localStreamRef.current.getTracks().forEach((track) => track.stop());
      } catch (err) {
        console.error("Error stopping local stream tracks:", err);
      }
      localStreamRef.current = null;
    }

    setLocalStream(null);
    setRemoteStream(null);
    setCallerInfo(null);
    setCallType(null);
    setIsMuted(false);
    setIsCameraOff(false);
    setDuration(0);
    setIsFullscreen(false);
    userManuallySelectedRef.current = false;
  }, [stopRingtone, stopDialtone]);

  // Keep callback refs in sync
  useEffect(() => {
    cleanupRef.current = cleanupMediaAndConnection;
  }, [cleanupMediaAndConnection]);
  useEffect(() => {
    playRingtoneRef.current = playRingtone;
  }, [playRingtone]);
  useEffect(() => {
    stopDialtoneRef.current = stopDialtone;
  }, [stopDialtone]);

  // Publish helper
  const publishSignal = useCallback(
    (type: string, data: object) => {
      if (stompClientRef.current && stompClientRef.current.connected) {
        stompClientRef.current.publish({
          destination: `/app/call.${type}`,
          body: JSON.stringify({ type, ...data }),
        });
      }
    },
    [stompClientRef]
  );

  useEffect(() => {
    publishSignalRef.current = publishSignal;
  }, [publishSignal]);

  // WebRTC ICE Setup
  const createPeerConnection = useCallback(
    (targetUserId: number) => {
      const pc = new RTCPeerConnection({
        iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
      });

      pc.onicecandidate = (event) => {
        if (event.candidate) {
          publishSignal("ice", {
            recipientId: targetUserId,
            candidate: event.candidate,
          });
        }
      };

      pc.oniceconnectionstatechange = async () => {
        console.log("ICE connection state changed:", pc.iceConnectionState);
        if (
          pc.iceConnectionState === "failed" ||
          pc.connectionState === "failed"
        ) {
          console.warn("ICE connection failed, attempting ICE restart...");
          try {
            const offer = await pc.createOffer({ iceRestart: true });
            await pc.setLocalDescription(offer);
            const targetInfo = callerInfoRef.current;
            if (targetInfo || targetUserId) {
              const recipientId = targetInfo ? targetInfo.id : targetUserId;
              publishSignalRef.current("offer", {
                recipientId,
                callType: callTypeRef.current || "VIDEO",
                sdp: offer.sdp,
                type: "offer",
              });
            }
          } catch (e) {
            console.error("Failed ICE restart:", e);
          }
        }
      };

      pc.ontrack = (event) => {
        console.log("WebRTC ontrack received:", event.track.kind);
        const handleTrackEnded = () => {
          setRemoteStream((prev) => {
            if (!prev) return null;
            const remaining = prev
              .getTracks()
              .filter((t) => t.readyState !== "ended");
            return new MediaStream(remaining);
          });
        };
        if (typeof event.track.addEventListener === "function") {
          event.track.removeEventListener("ended", handleTrackEnded);
          event.track.addEventListener("ended", handleTrackEnded);
        } else {
          event.track.onended = handleTrackEnded;
        }

        setRemoteStream((prev) => {
          const prevTracks = prev ? prev.getTracks() : [];
          const streamTracks =
            event.streams && event.streams[0]
              ? event.streams[0].getTracks()
              : [];
          const allTracks = [
            ...prevTracks,
            ...streamTracks,
            event.track,
          ].filter((t) => t.readyState !== "ended");
          // Deduplicate tracks by id
          const uniqueTracks = Array.from(
            new Map(allTracks.map((t) => [t.id, t])).values()
          );
          return new MediaStream(uniqueTracks);
        });
      };

      pcRef.current = pc;
      return pc;
    },
    [publishSignal]
  );

  // Endpoints Call handlers
  const initiateCall = useCallback(
    async (recipientId: number, type: CallType, recipientName?: string) => {
      cleanupMediaAndConnection();
      setCallType(type);
      setStatus("dialing");
      setCallerInfo({
        id: recipientId,
        fullName: recipientName || `User ${recipientId}`,
      });

      playDialtone();

      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        alert(
          "Media devices are not accessible. WebRTC requires a secure context (HTTPS or localhost).\n\n" +
            "If you are testing on a local IP, enable 'Insecure origins treated as secure' in your browser flags (e.g., chrome://flags/#unsafely-treat-insecure-origin-as-secure)."
        );
        setStatus("ended");
        cleanupMediaAndConnection();
        return;
      }

      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: type === "VIDEO",
        });
        localStreamRef.current = stream;
        setLocalStream(stream);
        refreshAudioDevices();

        const pc = createPeerConnection(recipientId);
        stream.getTracks().forEach((track) => {
          const sender = pc.addTrack(track, stream);
          if (track.kind === "video") {
            configureVideoSender(sender);
          }
        });

        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);

        publishSignal("offer", {
          recipientId,
          callType: type,
          sdp: offer.sdp,
          type: "offer",
        });

        // Start 30s unanswered timeout
        unansweredTimeoutRef.current = setTimeout(() => {
          publishSignal("cancel", { recipientId, type: "missed" });
          setStatus("ended");
          cleanupMediaAndConnection();
        }, 30000);
      } catch (err) {
        console.error("Failed to get local stream or negotiate calling: ", err);
        setStatus("ended");
        cleanupMediaAndConnection();
      }
    },
    [
      cleanupMediaAndConnection,
      createPeerConnection,
      playDialtone,
      publishSignal,
      refreshAudioDevices,
    ]
  );

  const acceptCall = useCallback(async () => {
    const currentCaller = callerInfoRef.current || callerInfo;
    if (!currentCaller || status !== "ringing") return;
    stopRingtone();
    setStatus("connecting");

    if (unansweredTimeoutRef.current) {
      clearTimeout(unansweredTimeoutRef.current);
      unansweredTimeoutRef.current = null;
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      alert(
        "Media devices are not accessible. WebRTC requires a secure context (HTTPS or localhost).\n\n" +
          "If you are testing on a local IP, enable 'Insecure origins treated as secure' in your browser flags (e.g., chrome://flags/#unsafely-treat-insecure-origin-as-secure)."
      );
      setStatus("ended");
      cleanupMediaAndConnection();
      return;
    }

    try {
      const isVideo = callType === "VIDEO";
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: isVideo,
      });
      localStreamRef.current = stream;
      setLocalStream(stream);
      refreshAudioDevices();

      const pc = createPeerConnection(currentCaller.id);
      stream.getTracks().forEach((track) => {
        const sender = pc.addTrack(track, stream);
        if (track.kind === "video") {
          configureVideoSender(sender);
        }
      });

      if (currentCaller.sdp) {
        await pc.setRemoteDescription(
          new RTCSessionDescription({ type: "offer", sdp: currentCaller.sdp })
        );

        // Process queued ICE candidates
        const queuedCandidates = iceQueueRef.current;
        iceQueueRef.current = [];
        for (const cand of queuedCandidates) {
          try {
            await pc.addIceCandidate(new RTCIceCandidate(cand));
          } catch (err) {
            console.error(
              "Failed to add queued ICE candidate on accept: ",
              err
            );
          }
        }

        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);

        publishSignal("answer", {
          recipientId: currentCaller.id,
          sdp: answer.sdp,
          type: "answer",
        });

        setStatus("active");
        setDuration(0);
        durationIntervalRef.current = setInterval(() => {
          setDuration((prev) => prev + 1);
        }, 1000);
      }
    } catch (err) {
      console.error("Error accepting call: ", err);
      setStatus("ended");
      cleanupMediaAndConnection();
    }
  }, [
    callerInfo,
    status,
    callType,
    createPeerConnection,
    publishSignal,
    stopRingtone,
    cleanupMediaAndConnection,
    refreshAudioDevices,
  ]);

  const rejectCall = useCallback(() => {
    const targetInfo = callerInfoRef.current || callerInfo;
    if (targetInfo) {
      publishSignal("reject", { recipientId: targetInfo.id });
    }
    setStatus("ended");
    cleanupMediaAndConnection();
  }, [callerInfo, publishSignal, cleanupMediaAndConnection]);

  const cancelCall = useCallback(() => {
    const targetInfo = callerInfoRef.current || callerInfo;
    if (targetInfo) {
      publishSignal("cancel", { recipientId: targetInfo.id });
    }
    setStatus("ended");
    cleanupMediaAndConnection();
  }, [callerInfo, publishSignal, cleanupMediaAndConnection]);

  const hangupCall = useCallback(() => {
    const targetInfo = callerInfoRef.current || callerInfo;
    if (targetInfo) {
      publishSignal("hangup", { recipientId: targetInfo.id });
    }
    setStatus("ended");
    cleanupMediaAndConnection();
  }, [callerInfo, publishSignal, cleanupMediaAndConnection]);

  const toggleMute = useCallback(() => {
    if (localStreamRef.current) {
      const audioTrack = localStreamRef.current.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = !audioTrack.enabled;
        setIsMuted(!audioTrack.enabled);
      }
    }
  }, []);

  const toggleCamera = useCallback(async () => {
    if (!localStreamRef.current) return;

    const videoTrack = localStreamRef.current.getVideoTracks()[0];
    if (videoTrack) {
      // Toggle existing track
      videoTrack.enabled = !videoTrack.enabled;
      setIsCameraOff(!videoTrack.enabled);
    } else {
      // Upgrade call: acquire video stream track mid-call
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: true,
        });
        const newTrack = stream.getVideoTracks()[0];
        if (newTrack) {
          localStreamRef.current.addTrack(newTrack);
          setLocalStream(new MediaStream(localStreamRef.current.getTracks()));
          setIsCameraOff(false);
          setCallType("VIDEO");

          const activeCaller = callerInfoRef.current || callerInfo;
          if (pcRef.current && activeCaller) {
            const pc = pcRef.current;
            const transceivers =
              typeof pc.getTransceivers === "function"
                ? pc.getTransceivers()
                : [];
            const videoTransceiver = transceivers.find(
              (t) =>
                t &&
                (t.receiver?.track?.kind === "video" ||
                  t.sender?.track?.kind === "video")
            );

            if (videoTransceiver) {
              if (
                videoTransceiver.sender &&
                typeof videoTransceiver.sender.replaceTrack === "function"
              ) {
                await videoTransceiver.sender.replaceTrack(newTrack);
                configureVideoSender(videoTransceiver.sender);
              }
              videoTransceiver.direction = "sendrecv";
            } else {
              const sender = pc.addTrack(newTrack, localStreamRef.current);
              configureVideoSender(sender);
            }

            // Trigger renegotiation
            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);
            publishSignalRef.current("offer", {
              recipientId: activeCaller.id,
              callType: "VIDEO",
              sdp: offer.sdp,
              type: "offer",
            });
          }
        }
      } catch (err) {
        console.error("Failed to acquire video track for upgrade: ", err);
      }
    }
  }, [callerInfo]);

  // Dispatch background notification
  useEffect(() => {
    if (
      status === "ringing" &&
      typeof window !== "undefined" &&
      typeof document !== "undefined"
    ) {
      if (document.hidden) {
        showNotification("Incoming Call", {
          body: `Incoming ${callType} call from ${callerInfo?.fullName || callerInfo?.username || "Contact"}`,
        });
      }
    }
  }, [status, callType, callerInfo]);

  // Handle mobile device document visibility changes
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden && status === "ringing") {
        if (
          "serviceWorker" in navigator &&
          navigator.serviceWorker.controller
        ) {
          navigator.serviceWorker.controller.postMessage({
            type: "INCOMING_CALL",
            callerName:
              callerInfo?.fullName || callerInfo?.username || "Contact",
            callType,
          });
        }
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () =>
      document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [status, callType, callerInfo]);

  // STOMP Call Signaling Subscriber
  useEffect(() => {
    if (!userId) return;

    const unsubscribe = subscribe(
      "/user/queue/call",
      async (message: { body: string }) => {
        const signal = JSON.parse(message.body);
        const {
          senderId,
          type,
          sdp,
          candidate,
          callType: sigCallType,
          senderFullName,
          senderUsername,
          fullName,
          username,
        } = signal;

        // Read current state from refs to avoid stale closures
        const currentStatus = statusRef.current;
        const currentCallerInfo = callerInfoRef.current;

        switch (type) {
          case "offer":
            // Check for simultaneous call (glare resolution)
            if (
              currentStatus === "dialing" &&
              Number(currentCallerInfo?.id) === Number(senderId)
            ) {
              // Lower userId wins
              if (Number(userId) > Number(senderId)) {
                // Higher userId cancels own call and switches to receiving offer
                stopDialtoneRef.current();
                if (unansweredTimeoutRef.current) {
                  clearTimeout(unansweredTimeoutRef.current);
                  unansweredTimeoutRef.current = null;
                }
                if (pcRef.current) {
                  pcRef.current.close();
                  pcRef.current = null;
                }
                // Switch to Ringing state
                setCallType(sigCallType);
                setStatus("ringing");
                setCallerInfo({
                  id: senderId,
                  sdp,
                  ...(senderFullName || fullName
                    ? { fullName: senderFullName || fullName }
                    : currentCallerInfo?.fullName
                      ? { fullName: currentCallerInfo.fullName }
                      : {}),
                  ...(senderUsername || username
                    ? { username: senderUsername || username }
                    : currentCallerInfo?.username
                      ? { username: currentCallerInfo.username }
                      : {}),
                });
                playRingtoneRef.current();
                return;
              } else {
                // Lower userId ignores incoming offer, their own offer takes priority
                console.log(
                  "Simultaneous call: Ignoring incoming offer as lower userId."
                );
                return;
              }
            }

            // Handle in-call renegotiation (e.g., upgrading from AUDIO to VIDEO)
            if (
              currentStatus === "active" &&
              Number(currentCallerInfo?.id) === Number(senderId) &&
              pcRef.current
            ) {
              try {
                if (sigCallType) {
                  setCallType(sigCallType);
                }
                const pc = pcRef.current;
                await pc.setRemoteDescription(
                  new RTCSessionDescription({ type: "offer", sdp })
                );
                const answer = await pc.createAnswer();
                await pc.setLocalDescription(answer);
                publishSignalRef.current("answer", {
                  recipientId: senderId,
                  sdp: answer.sdp,
                  type: "answer",
                });
              } catch (err) {
                console.error(
                  "Failed to handle in-call renegotiation offer:",
                  err
                );
              }
              return;
            }

            if (currentStatus !== "idle" && currentStatus !== "ended") {
              // If already in an active call or ringing/dialing, we shouldn't receive this, but if we do, do nothing
              return;
            }

            setCallType(sigCallType);
            setStatus("ringing");
            setCallerInfo({
              id: senderId,
              sdp,
              ...(senderFullName || fullName
                ? { fullName: senderFullName || fullName }
                : {}),
              ...(senderUsername || username
                ? { username: senderUsername || username }
                : {}),
            });
            playRingtoneRef.current();

            // 30s timeout on callee side
            unansweredTimeoutRef.current = setTimeout(() => {
              setStatus("ended");
              cleanupRef.current();
            }, 30000);
            break;

          case "answer":
            if (
              (currentStatus === "dialing" || currentStatus === "active") &&
              pcRef.current
            ) {
              if (currentStatus === "dialing") {
                stopDialtoneRef.current();
                if (unansweredTimeoutRef.current) {
                  clearTimeout(unansweredTimeoutRef.current);
                  unansweredTimeoutRef.current = null;
                }
              }
              try {
                const pc = pcRef.current;
                await pc.setRemoteDescription(
                  new RTCSessionDescription({ type: "answer", sdp })
                );

                if (currentStatus === "dialing") {
                  // Process queued ICE candidates
                  const queuedCandidates = iceQueueRef.current;
                  iceQueueRef.current = [];
                  for (const cand of queuedCandidates) {
                    try {
                      await pc.addIceCandidate(new RTCIceCandidate(cand));
                    } catch (err) {
                      console.error(
                        "Failed to add queued ICE candidate on answer: ",
                        err
                      );
                    }
                  }

                  setStatus("active");
                  setDuration(0);
                  durationIntervalRef.current = setInterval(() => {
                    setDuration((prev) => prev + 1);
                  }, 1000);
                }
              } catch (err) {
                console.error("Failed to set remote description answer: ", err);
                if (currentStatus === "dialing") {
                  setStatus("ended");
                  cleanupRef.current();
                }
              }
            }
            break;

          case "ice":
            if (candidate) {
              const pc = pcRef.current;
              if (pc && pc.remoteDescription) {
                try {
                  await pc.addIceCandidate(new RTCIceCandidate(candidate));
                } catch (err) {
                  console.error("Failed to add ICE candidate: ", err);
                }
              } else {
                iceQueueRef.current.push(candidate);
              }
            }
            break;

          case "cancel":
          case "reject":
          case "hangup":
            setStatus("ended");
            cleanupRef.current();
            break;

          case "busy":
            if (currentStatus === "dialing") {
              alert("User is currently busy on another call.");
              setStatus("ended");
              cleanupRef.current();
            }
            break;
        }
      }
    );

    return () => {
      unsubscribe();
    };
  }, [userId, subscribe]);

  return (
    <CallContext.Provider
      value={{
        status,
        callerInfo,
        localStream,
        remoteStream,
        isMuted,
        isCameraOff,
        callType,
        isFullscreen,
        duration,
        audioOutputDevices,
        selectedAudioOutputId,
        switchAudioOutput,
        initiateCall,
        acceptCall,
        rejectCall,
        cancelCall,
        hangupCall,
        toggleMute,
        toggleCamera,
        setIsFullscreen,
      }}
    >
      {children}
    </CallContext.Provider>
  );
};

export const useCall = () => {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error("useCall must be used within a CallProvider");
  }
  return context;
};
