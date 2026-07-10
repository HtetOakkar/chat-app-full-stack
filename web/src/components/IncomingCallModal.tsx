"use client";

import React from "react";
import { useCall } from "@/context/CallContext";
import { useTranslations } from "@/lib/i18n";

export default function IncomingCallModal() {
  const t = useTranslations();
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
          {t.incomingCall} {callType === "VIDEO" ? t.video : t.audio} {t.call}
          ...
        </p>

        <div className="flex gap-6 w-full justify-center">
          <button
            type="button"
            onClick={rejectCall}
            aria-label={t.decline}
            className="w-16 h-16 rounded-full bg-error hover:bg-error/90 text-on-error shadow-lg shadow-error/20 transition-colors flex items-center justify-center"
          >
            <span
              className="material-symbols-outlined text-3xl"
              aria-hidden="true"
            >
              call_end
            </span>
          </button>

          <button
            type="button"
            onClick={acceptCall}
            aria-label={t.accept}
            className="w-16 h-16 rounded-full bg-tertiary hover:bg-tertiary/90 text-on-tertiary shadow-lg shadow-tertiary/20 transition-colors flex items-center justify-center"
          >
            <span
              className="material-symbols-outlined text-3xl"
              aria-hidden="true"
            >
              call
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
