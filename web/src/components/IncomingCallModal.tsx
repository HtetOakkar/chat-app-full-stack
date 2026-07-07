"use client";

import React from "react";
import { useCall } from "@/context/CallContext";

export default function IncomingCallModal() {
  const { status, callerInfo, callType, acceptCall, rejectCall } = useCall();

  if (status !== "ringing" || !callerInfo) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-[fadeIn_0.2s_ease-out]">
      <div className="bg-surface-container-lowest border border-outline-variant/30 rounded-2xl p-6 shadow-2xl max-w-sm w-full mx-4 flex flex-col items-center text-center">
        <div className="w-16 h-16 rounded-full bg-primary/10 text-primary border border-primary/20 flex items-center justify-center font-bold text-xl mb-4">
          {(callerInfo.fullName || callerInfo.username || "C")
            .charAt(0)
            .toUpperCase()}
        </div>

        <h3 className="text-lg font-bold text-on-surface mb-1">
          {callerInfo.fullName ||
            callerInfo.username ||
            `User ${callerInfo.id}`}
        </h3>

        <p className="text-xs text-outline mb-6">
          Incoming {callType === "VIDEO" ? "Video" : "Audio"} Call...
        </p>

        <div className="flex gap-4 w-full">
          <button
            type="button"
            onClick={rejectCall}
            className="flex-1 py-3 px-4 rounded-xl bg-error/15 hover:bg-error/25 text-error text-xs font-bold uppercase tracking-wider transition-colors flex items-center justify-center gap-2 border border-error/20"
          >
            <span className="material-symbols-outlined text-base">
              call_end
            </span>
            Decline
          </button>

          <button
            type="button"
            onClick={acceptCall}
            className="flex-1 py-3 px-4 rounded-xl bg-tertiary/15 hover:bg-tertiary/25 text-tertiary text-xs font-bold uppercase tracking-wider transition-colors flex items-center justify-center gap-2 border border-tertiary/20"
          >
            <span className="material-symbols-outlined text-base">call</span>
            Accept
          </button>
        </div>
      </div>
    </div>
  );
}
