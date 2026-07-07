"use client";

import React, { useEffect, useRef, useState } from "react";
import { useCall } from "@/context/CallContext";

export default function ActiveCallOverlay() {
  const {
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
    cancelCall,
    hangupCall,
    toggleMute,
    toggleCamera,
    setIsFullscreen,
  } = useCall();

  const [showAudioMenu, setShowAudioMenu] = useState(false);

  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);

  // Bind local stream
  // Bind local stream
  useEffect(() => {
    if (localVideoRef.current && localStream) {
      localVideoRef.current.srcObject = localStream;
      try {
        const playPromise = localVideoRef.current.play?.();
        if (playPromise && typeof playPromise.catch === "function") {
          playPromise.catch((err) =>
            console.warn("Failed to play local video:", err)
          );
        }
      } catch (err) {
        /* ignore jsdom not-implemented error */
      }
    }
  }, [localStream, status, isCameraOff, callType]);

  // Bind remote stream
  useEffect(() => {
    if (remoteVideoRef.current && remoteStream) {
      const videoEl = remoteVideoRef.current;
      videoEl.srcObject = remoteStream;
      const attemptPlay = () => {
        try {
          const playPromise = videoEl.play?.();
          if (playPromise && typeof playPromise.catch === "function") {
            playPromise.catch((err) =>
              console.warn("Failed to play remote video:", err)
            );
          }
        } catch (err) {
          /* ignore jsdom not-implemented error */
        }
      };

      attemptPlay();

      const tracks =
        typeof remoteStream.getTracks === "function"
          ? remoteStream.getTracks()
          : [];
      tracks.forEach((track: any) => {
        if (track && typeof track.addEventListener === "function") {
          track.addEventListener("unmute", attemptPlay);
        }
      });

      return () => {
        tracks.forEach((track: any) => {
          if (track && typeof track.removeEventListener === "function") {
            track.removeEventListener("unmute", attemptPlay);
          }
        });
      };
    }
  }, [remoteStream, status, callType]);

  // Bind remote stream to audio element (for audio-only calls)
  useEffect(() => {
    if (remoteAudioRef.current && remoteStream && callType === "AUDIO") {
      remoteAudioRef.current.srcObject = remoteStream;
    }
  }, [remoteStream, status, callType]);

  // Apply audio sink ID to remote media elements
  useEffect(() => {
    const targetId = selectedAudioOutputId || "";
    if (
      remoteVideoRef.current &&
      typeof (remoteVideoRef.current as any).setSinkId === "function"
    ) {
      (remoteVideoRef.current as any)
        .setSinkId(targetId)
        .catch((err: any) =>
          console.warn("Failed to set sink id on remote video:", err)
        );
    }
    if (
      remoteAudioRef.current &&
      typeof (remoteAudioRef.current as any).setSinkId === "function"
    ) {
      (remoteAudioRef.current as any)
        .setSinkId(targetId)
        .catch((err: any) =>
          console.warn("Failed to set sink id on remote audio:", err)
        );
    }
  }, [selectedAudioOutputId, status, remoteStream, callType]);

  if (status === "idle" || status === "ringing" || status === "ended")
    return null;

  const formatDuration = (secs: number) => {
    const m = Math.floor(secs / 60)
      .toString()
      .padStart(2, "0");
    const s = (secs % 60).toString().padStart(2, "0");
    return `${m}:${s}`;
  };

  const partnerName =
    callerInfo?.fullName ||
    callerInfo?.username ||
    `User ${callerInfo?.id || ""}`;

  return (
    <div
      className={`fixed inset-0 z-50 bg-black flex flex-col transition-all duration-300 ${
        isFullscreen ? "p-0" : "p-4 md:p-6"
      }`}
    >
      {/* Top Header info */}
      <div className="absolute top-6 left-6 z-10 flex flex-col">
        <span className="text-white font-bold text-base tracking-wide drop-shadow-md">
          {partnerName}
        </span>
        <span className="text-white/60 text-xs drop-shadow-md">
          {status === "dialing" && "Calling..."}
          {status === "connecting" && "Connecting..."}
          {status === "active" && formatDuration(duration)}
        </span>
      </div>

      {/* Video Streams Container */}
      <div className="flex-1 relative w-full h-full rounded-2xl overflow-hidden bg-zinc-950 flex items-center justify-center">
        {status === "active" && callType === "VIDEO" ? (
          <>
            {/* Remote Stream (Large main window) */}
            <div className="w-full h-full relative">
              <video
                ref={remoteVideoRef}
                autoPlay
                playsInline
                className="w-full h-full object-contain"
                data-testid="remote-video"
              />
              {!remoteStream && (
                <div className="absolute inset-0 flex items-center justify-center bg-zinc-900">
                  <div className="w-20 h-20 rounded-full bg-primary/10 text-primary border border-primary/20 flex items-center justify-center font-bold text-2xl">
                    {partnerName.charAt(0).toUpperCase()}
                  </div>
                </div>
              )}
            </div>

            {/* Local Stream (Small PIP box) */}
            <div className="absolute bottom-6 right-6 w-32 h-44 rounded-xl overflow-hidden border border-white/20 shadow-2xl bg-zinc-900 z-10">
              {!isCameraOff && localStream ? (
                <video
                  ref={localVideoRef}
                  autoPlay
                  playsInline
                  muted
                  className="w-full h-full object-contain transform -scale-x-100"
                  data-testid="local-video"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-white/40 text-xs">
                  Camera Off
                </div>
              )}
            </div>
          </>
        ) : (
          /* Calling / Connecting / Audio-only mode (Displays profile avatar card) */
          <div className="flex flex-col items-center justify-center text-center">
            <div className="w-24 h-24 rounded-full bg-primary/10 text-primary border border-primary/20 flex items-center justify-center font-bold text-3xl mb-4 animate-pulse">
              {partnerName.charAt(0).toUpperCase()}
            </div>
            <h4 className="text-white text-lg font-bold mb-1">{partnerName}</h4>
            <p className="text-white/40 text-xs uppercase tracking-widest font-semibold">
              {status === "dialing" ? "Dialing..." : "Audio Call Active"}
            </p>
          </div>
        )}
      </div>

      {/* Calling Controls Panel */}
      <div className="absolute bottom-10 left-1/2 transform -translate-x-1/2 z-20 bg-zinc-900/80 backdrop-blur-md px-6 py-3.5 rounded-full flex items-center gap-6 shadow-2xl border border-white/5">
        {/* Toggle Audio Mute */}
        <button
          type="button"
          onClick={toggleMute}
          className={`w-11 h-11 rounded-full flex items-center justify-center transition-colors ${
            isMuted
              ? "bg-error text-white hover:bg-error/90"
              : "bg-white/10 text-white hover:bg-white/25"
          }`}
          title={isMuted ? "Unmute Microphone" : "Mute Microphone"}
        >
          <span className="material-symbols-outlined text-lg">
            {isMuted ? "mic_off" : "mic"}
          </span>
        </button>

        {/* Toggle Video Camera (Upgrade to Video Call) */}
        <button
          type="button"
          onClick={toggleCamera}
          className={`w-11 h-11 rounded-full flex items-center justify-center transition-colors ${
            isCameraOff || callType === "AUDIO"
              ? "bg-white/10 text-white hover:bg-white/25"
              : "bg-primary text-white hover:bg-primary/90"
          }`}
          title={
            isCameraOff || callType === "AUDIO"
              ? "Turn Camera On"
              : "Turn Camera Off"
          }
        >
          <span className="material-symbols-outlined text-lg">
            {isCameraOff || callType === "AUDIO" ? "videocam_off" : "videocam"}
          </span>
        </button>

        {/* Switch Audio Source */}
        {audioOutputDevices && audioOutputDevices.length > 0 && (
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowAudioMenu(!showAudioMenu)}
              className="w-11 h-11 rounded-full bg-white/10 text-white hover:bg-white/25 flex items-center justify-center transition-colors"
              title="Switch Audio Source"
            >
              <span className="material-symbols-outlined text-lg">
                volume_up
              </span>
            </button>

            {showAudioMenu && (
              <div className="absolute bottom-14 left-1/2 transform -translate-x-1/2 bg-zinc-900 border border-white/10 rounded-xl py-2 px-1 shadow-2xl min-w-[180px] z-50 flex flex-col gap-1">
                <div className="text-[10px] text-white/40 uppercase tracking-wider px-3 py-1 font-semibold border-b border-white/10 mb-1">
                  Audio Output
                </div>
                {audioOutputDevices.map((device) => {
                  const isSelected = selectedAudioOutputId === device.deviceId;
                  return (
                    <button
                      key={device.deviceId}
                      type="button"
                      onClick={() => {
                        switchAudioOutput(device.deviceId);
                        setShowAudioMenu(false);
                      }}
                      className={`text-left px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-between transition-colors ${
                        isSelected
                          ? "bg-primary text-white"
                          : "text-white/80 hover:bg-white/10"
                      }`}
                    >
                      <span className="truncate max-w-[140px]">
                        {device.label}
                      </span>
                      {isSelected && (
                        <span className="material-symbols-outlined text-sm ml-2">
                          check
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Fullscreen Toggle */}
        <button
          type="button"
          onClick={() => setIsFullscreen(!isFullscreen)}
          className="w-11 h-11 rounded-full bg-white/10 text-white hover:bg-white/25 flex items-center justify-center transition-colors"
          title={isFullscreen ? "Exit Fullscreen" : "Enter Fullscreen"}
        >
          <span className="material-symbols-outlined text-lg">
            {isFullscreen ? "fullscreen_exit" : "fullscreen"}
          </span>
        </button>

        {/* Hangup / Cancel Call */}
        <button
          type="button"
          onClick={status === "dialing" ? cancelCall : hangupCall}
          className="w-12 h-12 rounded-full bg-error text-white hover:bg-error/90 flex items-center justify-center transition-colors shadow-lg"
          title="End Call"
        >
          <span className="material-symbols-outlined text-xl">call_end</span>
        </button>
      </div>

      {/* Hidden audio element for audio-only remote stream */}
      {status === "active" && callType === "AUDIO" && (
        <audio
          ref={remoteAudioRef}
          autoPlay
          playsInline
          className="hidden"
          data-testid="remote-audio"
        />
      )}
    </div>
  );
}
