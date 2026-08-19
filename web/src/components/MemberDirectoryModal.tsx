"use client";

import React, { useState } from "react";
import {
  useWorkspace,
  type WorkspaceMemberDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useCall } from "@/context/CallContext";

interface MemberDirectoryModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function MemberDirectoryModal({
  isOpen,
  onClose,
}: MemberDirectoryModalProps) {
  const { currentWorkspace, members, presenceMap, openDirectMessage } =
    useWorkspace();
  const { userId } = useAuth();
  const { initiateCall } = useCall();
  const [search, setSearch] = useState("");

  if (!isOpen || !currentWorkspace) return null;

  const filteredMembers = members.filter((m: WorkspaceMemberDto) => {
    if (m.userId === userId) return false;
    const q = search.toLowerCase();
    return (
      m.username.toLowerCase().includes(q) ||
      (m.fullName && m.fullName.toLowerCase().includes(q))
    );
  });

  const handleMessage = async (targetUserId: number) => {
    await openDirectMessage(targetUserId);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-lg w-full max-h-[80vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary font-bold">
              <span className="material-symbols-outlined text-base">group</span>
            </div>
            <div>
              <h3 className="font-bold text-sm text-on-surface">
                Member Directory
              </h3>
              <p className="text-[11px] text-outline">
                {currentWorkspace.name} ({members.length} members)
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        {/* Search */}
        <div className="p-4 border-b border-outline-variant/15 bg-surface-container-low/20">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-base">
              search
            </span>
            <input
              type="text"
              placeholder="Search by name or username..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors"
            />
          </div>
        </div>

        {/* Members List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {filteredMembers.length === 0 ? (
            <div className="text-center py-8 text-outline text-xs">
              No colleagues found.
            </div>
          ) : (
            filteredMembers.map((member: WorkspaceMemberDto) => {
              const isOnline = presenceMap[member.userId] === "ONLINE";
              return (
                <div
                  key={member.id}
                  className="flex items-center justify-between p-3 rounded-2xl bg-surface-container-low hover:bg-surface-container transition-colors border border-outline-variant/10"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="relative shrink-0">
                      <div className="w-10 h-10 rounded-2xl bg-primary/10 flex items-center justify-center font-bold text-primary text-sm">
                        {member.username.charAt(0).toUpperCase()}
                      </div>
                      <span
                        className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full ring-2 ring-surface-container-lowest ${
                          isOnline ? "bg-emerald-500" : "bg-neutral-400"
                        }`}
                      />
                    </div>
                    <div className="min-w-0">
                      <span className="font-bold text-xs text-on-surface block truncate">
                        {member.fullName || member.username}
                      </span>
                      <span className="text-[11px] text-outline block truncate">
                        @{member.username} •{" "}
                        <span className="capitalize">
                          {member.role.toLowerCase()}
                        </span>
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => initiateCall(member.userId, "AUDIO")}
                      title="Audio Call"
                      className="w-8 h-8 rounded-xl bg-surface-container-high hover:bg-primary/10 text-outline hover:text-primary flex items-center justify-center transition-colors"
                    >
                      <span className="material-symbols-outlined text-base">
                        call
                      </span>
                    </button>
                    <button
                      type="button"
                      onClick={() => initiateCall(member.userId, "VIDEO")}
                      title="Video Call"
                      className="w-8 h-8 rounded-xl bg-surface-container-high hover:bg-primary/10 text-outline hover:text-primary flex items-center justify-center transition-colors"
                    >
                      <span className="material-symbols-outlined text-base">
                        videocam
                      </span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleMessage(member.userId)}
                      className="px-3 py-1.5 rounded-xl bg-primary text-white text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs"
                    >
                      Message
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
