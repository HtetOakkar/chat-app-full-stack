"use client";

import React from "react";
import { useWorkspace } from "@/context/WorkspaceContext";

/**
 * Tells a removed User who removed them from a Private Channel (#82). The
 * notice arrives on their personal queue only, and this component is mounted
 * at the dashboard root so it shows wherever they are in the app.
 */
export default function ChannelRemovalNotice() {
  const { channelRemovalNotice, dismissChannelRemovalNotice } = useWorkspace();

  if (!channelRemovalNotice) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed top-3 inset-x-4 md:inset-x-auto md:right-6 md:w-96 z-[90] flex items-start gap-3 bg-surface-container-lowest border border-outline-variant/30 rounded-2xl shadow-2xl p-4"
    >
      <span className="material-symbols-outlined text-[20px] text-primary shrink-0 mt-0.5">
        lock_person
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-bold text-on-surface">
          Removed from channel
        </p>
        <p className="text-xs text-outline mt-0.5 break-words">
          @{channelRemovalNotice.removedByUsername} removed you from #
          {channelRemovalNotice.channelName}
        </p>
      </div>
      <button
        type="button"
        aria-label="Dismiss"
        onClick={dismissChannelRemovalNotice}
        className="shrink-0 -m-2 min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl text-outline hover:bg-surface-container-high/60 transition-colors"
      >
        <span className="material-symbols-outlined text-[18px]">close</span>
      </button>
    </div>
  );
}
