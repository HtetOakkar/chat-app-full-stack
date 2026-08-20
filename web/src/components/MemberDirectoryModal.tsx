"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  useWorkspace,
  type WorkspaceMemberDto,
  type InvitationDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useCall } from "@/context/CallContext";

interface MemberDirectoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenInvite?: () => void;
}

export default function MemberDirectoryModal({
  isOpen,
  onClose,
  onOpenInvite,
}: MemberDirectoryModalProps) {
  const {
    currentWorkspace,
    members,
    presenceMap,
    openDirectMessage,
    getPendingInvitations,
    resendInvitation,
    revokeInvitation,
  } = useWorkspace();
  const { userId } = useAuth();
  const { initiateCall } = useCall();

  const [tab, setTab] = useState<"members" | "pending">("members");
  const [search, setSearch] = useState("");
  const [pendingInvites, setPendingInvites] = useState<InvitationDto[]>([]);
  const [loadingPending, setLoadingPending] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

  const isAdminOrOwner =
    currentWorkspace?.role === "OWNER" || currentWorkspace?.role === "ADMIN";

  const fetchPending = useCallback(async () => {
    if (!currentWorkspace || !isAdminOrOwner) return;
    setLoadingPending(true);
    try {
      const list = await getPendingInvitations();
      setPendingInvites(list || []);
    } catch (err: any) {
      console.warn("Failed to load pending invitations:", err);
    } finally {
      setLoadingPending(false);
    }
  }, [currentWorkspace, isAdminOrOwner, getPendingInvitations]);

  useEffect(() => {
    if (
      isOpen &&
      isAdminOrOwner &&
      (tab === "pending" || pendingInvites.length === 0)
    ) {
      fetchPending();
    }
  }, [isOpen, tab, isAdminOrOwner, fetchPending]);

  if (!isOpen || !currentWorkspace) return null;

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  const handleResend = async (id: number, email?: string) => {
    setActionLoadingId(id);
    try {
      await resendInvitation(id);
      showToast(`Invitation re-sent to ${email || "recipient"}!`);
    } catch (err: any) {
      showToast(err?.message || "Failed to resend invitation.");
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleRevoke = async (id: number, email?: string) => {
    setActionLoadingId(id);
    const previous = [...pendingInvites];
    setPendingInvites((prev) => prev.filter((inv) => inv.id !== id));
    try {
      await revokeInvitation(id);
      showToast(`Invitation revoked for ${email || "recipient"}.`);
    } catch (err: any) {
      setPendingInvites(previous);
      showToast(err?.message || "Failed to revoke invitation.");
    } finally {
      setActionLoadingId(null);
    }
  };

  const filteredMembers = members.filter((m: WorkspaceMemberDto) => {
    if (m.userId === userId) return false;
    const q = search.toLowerCase();
    return (
      m.username.toLowerCase().includes(q) ||
      (m.fullName && m.fullName.toLowerCase().includes(q))
    );
  });

  const filteredPending = pendingInvites.filter((inv) => {
    const q = search.toLowerCase();
    return (
      (inv.email && inv.email.toLowerCase().includes(q)) ||
      (inv.inviterName && inv.inviterName.toLowerCase().includes(q))
    );
  });

  const handleMessage = async (targetUserId: number) => {
    await openDirectMessage(targetUserId);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-t-3xl sm:rounded-3xl shadow-2xl max-w-lg w-full max-h-[90dvh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between shrink-0">
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
          <div className="flex items-center gap-2">
            {onOpenInvite && (
              <button
                type="button"
                onClick={onOpenInvite}
                className="px-3 py-1.5 rounded-xl bg-primary/10 text-primary hover:bg-primary/20 text-xs font-bold transition-colors flex items-center gap-1.5"
              >
                <span className="material-symbols-outlined text-sm">
                  person_add
                </span>
                <span>Invite</span>
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              aria-label="Close directory"
              className="w-9 h-9 sm:w-8 sm:h-8 rounded-xl text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          </div>
        </div>

        {/* Tab Buttons (if Admin/Owner) */}
        {isAdminOrOwner && (
          <div className="flex border-b border-outline-variant/15 px-6 gap-6 bg-surface-container-low/30 shrink-0">
            <button
              type="button"
              onClick={() => setTab("members")}
              className={`py-3 text-xs font-bold border-b-2 transition-all ${
                tab === "members"
                  ? "border-primary text-primary"
                  : "border-transparent text-outline hover:text-on-surface"
              }`}
            >
              Members ({members.length})
            </button>
            <button
              type="button"
              onClick={() => setTab("pending")}
              className={`py-3 text-xs font-bold border-b-2 transition-all flex items-center gap-1.5 ${
                tab === "pending"
                  ? "border-primary text-primary"
                  : "border-transparent text-outline hover:text-on-surface"
              }`}
            >
              <span>Pending Invitations</span>
              {pendingInvites.length > 0 && (
                <span className="px-1.5 py-0.2 rounded-full bg-primary/10 text-primary text-[10px] font-bold">
                  {pendingInvites.length}
                </span>
              )}
            </button>
          </div>
        )}

        {/* Search */}
        <div className="p-4 border-b border-outline-variant/15 bg-surface-container-low/20 shrink-0">
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-base">
              search
            </span>
            <input
              type="text"
              placeholder={
                tab === "members"
                  ? "Search colleagues by name or username..."
                  : "Search pending invites by email or inviter..."
              }
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors"
            />
          </div>
        </div>

        {/* Toast Feedback */}
        {toastMessage && (
          <div className="mx-4 mt-3 p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-xs font-semibold flex items-center justify-between shrink-0">
            <span>{toastMessage}</span>
            <button
              type="button"
              onClick={() => setToastMessage(null)}
              className="text-xs text-outline hover:text-on-surface"
            >
              ✕
            </button>
          </div>
        )}

        {/* List Content */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2 custom-scrollbar">
          {tab === "members" ? (
            filteredMembers.length === 0 ? (
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
            )
          ) : loadingPending ? (
            <div className="text-center py-8 text-outline text-xs animate-pulse">
              Loading pending invitations...
            </div>
          ) : filteredPending.length === 0 ? (
            <div className="text-center py-8 text-outline text-xs">
              No pending invitations found.
            </div>
          ) : (
            filteredPending.map((inv) => (
              <div
                key={inv.id}
                className="flex items-center justify-between p-3 rounded-2xl bg-surface-container-low hover:bg-surface-container transition-colors border border-outline-variant/10"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-2xl bg-surface-container-high flex items-center justify-center text-outline font-bold shrink-0">
                    <span className="material-symbols-outlined text-base">
                      mail
                    </span>
                  </div>
                  <div className="min-w-0">
                    <span className="font-bold text-xs text-on-surface block truncate">
                      {inv.email}
                    </span>
                    <span className="text-[11px] text-outline block truncate">
                      Invited by @{inv.inviterName || "admin"} •{" "}
                      <span className="capitalize">
                        {inv.role.toLowerCase()}
                      </span>
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => handleResend(inv.id, inv.email)}
                    disabled={actionLoadingId === inv.id}
                    title="Resend Invitation Email"
                    className="px-3 py-1.5 rounded-xl bg-surface-container-high hover:bg-primary/10 text-outline hover:text-primary text-xs font-bold transition-colors disabled:opacity-50 flex items-center gap-1"
                  >
                    <span className="material-symbols-outlined text-sm">
                      send
                    </span>
                    Resend
                  </button>
                  <button
                    type="button"
                    onClick={() => handleRevoke(inv.id, inv.email)}
                    disabled={actionLoadingId === inv.id}
                    title="Revoke Invitation"
                    className="px-3 py-1.5 rounded-xl bg-surface-container-high hover:bg-error/10 text-outline hover:text-error text-xs font-bold transition-colors disabled:opacity-50 flex items-center gap-1"
                  >
                    <span className="material-symbols-outlined text-sm">
                      cancel
                    </span>
                    Revoke
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
