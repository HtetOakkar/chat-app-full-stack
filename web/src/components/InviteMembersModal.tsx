"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  useWorkspace,
  type InvitationDto,
  type BatchInvitationItemResult,
} from "@/context/WorkspaceContext";
import { parseEmailTokens } from "@/lib/emailUtils";

interface InviteMembersModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function InviteMembersModal({
  isOpen,
  onClose,
}: InviteMembersModalProps) {
  const {
    currentWorkspace,
    channels,
    createBatchInvites,
    getShareableJoinLink,
    toggleShareableJoinLink,
    resetShareableJoinLink,
  } = useWorkspace();

  const [tab, setTab] = useState<"email" | "link">("email");

  // Email Batch State
  const [emailChips, setEmailChips] = useState<string[]>([]);
  const [currentEmailInput, setCurrentEmailInput] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
  const [customMessage, setCustomMessage] = useState("");
  const [selectedChannelIds, setSelectedChannelIds] = useState<number[]>([]);
  const [batchResults, setBatchResults] = useState<
    BatchInvitationItemResult[] | null
  >(null);

  // Link State
  const [shareableLink, setShareableLink] = useState<InvitationDto | null>(
    null
  );
  const [linkLoading, setLinkLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  // General State
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isAdminOrOwner =
    currentWorkspace?.role === "OWNER" || currentWorkspace?.role === "ADMIN";

  // Eligible channels for initial assignment: public channels + private channels user belongs to
  const eligibleChannels = useMemo(() => {
    return channels.filter((c) => c.type === "PUBLIC" || c.isMember);
  }, [channels]);

  // Pre-select default/general public channel on open
  useEffect(() => {
    if (
      isOpen &&
      eligibleChannels.length > 0 &&
      selectedChannelIds.length === 0
    ) {
      const generalChannel = eligibleChannels.find(
        (c) => c.slug === "general" || c.name.toLowerCase() === "general"
      );
      if (generalChannel) {
        setSelectedChannelIds([generalChannel.id]);
      } else if (eligibleChannels.length > 0) {
        setSelectedChannelIds([eligibleChannels[0].id]);
      }
    }
  }, [isOpen, eligibleChannels, selectedChannelIds.length]);

  // Fetch shareable join link
  const fetchJoinLink = useCallback(async () => {
    if (!currentWorkspace) return;
    setLinkLoading(true);
    setError(null);
    try {
      const link = await getShareableJoinLink();
      setShareableLink(link);
    } catch (err: any) {
      setError(err?.message || "Failed to load shareable link");
    } finally {
      setLinkLoading(false);
    }
  }, [currentWorkspace, getShareableJoinLink]);

  useEffect(() => {
    if (isOpen && tab === "link" && !shareableLink) {
      fetchJoinLink();
    }
  }, [isOpen, tab, shareableLink, fetchJoinLink]);

  if (!isOpen || !currentWorkspace) return null;

  // Add raw email text as chip(s)
  const addEmailTokens = (input: string) => {
    const tokens = parseEmailTokens(input);
    if (tokens.length === 0) return;

    setEmailChips((prev) => {
      const set = new Set(prev);
      tokens.forEach((t) => set.add(t));
      return Array.from(set);
    });
    setCurrentEmailInput("");
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (["Enter", ",", " ", "Tab"].includes(e.key)) {
      e.preventDefault();
      addEmailTokens(currentEmailInput);
    } else if (
      e.key === "Backspace" &&
      !currentEmailInput &&
      emailChips.length > 0
    ) {
      setEmailChips((prev) => prev.slice(0, -1));
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    const paste = e.clipboardData.getData("text");
    if (
      paste &&
      (paste.includes(",") ||
        paste.includes(" ") ||
        paste.includes("\n") ||
        paste.includes("@"))
    ) {
      e.preventDefault();
      addEmailTokens(paste);
    }
  };

  const removeChip = (indexToRemove: number) => {
    setEmailChips((prev) => prev.filter((_, idx) => idx !== indexToRemove));
  };

  const toggleChannelSelection = (chId: number) => {
    setSelectedChannelIds((prev) =>
      prev.includes(chId) ? prev.filter((id) => id !== chId) : [...prev, chId]
    );
  };

  const handleSendBatch = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setBatchResults(null);

    // If there's text currently typed in input, add it as a token first
    let allEmails = [...emailChips];
    if (currentEmailInput.trim()) {
      const extra = parseEmailTokens(currentEmailInput);
      const set = new Set([...allEmails, ...extra]);
      allEmails = Array.from(set);
    }

    if (allEmails.length === 0) {
      setError("Please specify at least one recipient email address.");
      return;
    }

    setLoading(true);
    try {
      const response = await createBatchInvites(
        allEmails,
        role,
        customMessage.trim() || undefined,
        selectedChannelIds.length > 0 ? selectedChannelIds : undefined
      );

      setBatchResults(response.results);
      if (response.sentCount > 0) {
        setSuccess(
          `Successfully dispatched ${response.sentCount} invitation${
            response.sentCount > 1 ? "s" : ""
          }!`
        );
        setEmailChips([]);
        setCurrentEmailInput("");
        setCustomMessage("");
      } else {
        setError("No invitations were dispatched. Review the outcomes below.");
      }
    } catch (err: any) {
      setError(err?.message || "Failed to dispatch batch invitations");
    } finally {
      setLoading(false);
    }
  };

  // Shareable Link Actions
  const handleToggleLinkActive = async () => {
    setLinkLoading(true);
    setError(null);
    try {
      const updated = await toggleShareableJoinLink();
      setShareableLink(updated);
    } catch (err: any) {
      setError(err?.message || "Failed to update link status");
    } finally {
      setLinkLoading(false);
    }
  };

  const handleResetLink = async () => {
    setLinkLoading(true);
    setError(null);
    setShowResetConfirm(false);
    try {
      const fresh = await resetShareableJoinLink();
      setShareableLink(fresh);
      setSuccess(
        "Shareable join link reset! Previous link tokens are now revoked."
      );
    } catch (err: any) {
      setError(err?.message || "Failed to reset join link");
    } finally {
      setLinkLoading(false);
    }
  };

  const handleCopyLink = () => {
    if (shareableLink) {
      const origin =
        typeof window !== "undefined" ? window.location.origin : "";
      const url = `${origin}/join/${shareableLink.token}`;
      navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const originUrl = typeof window !== "undefined" ? window.location.origin : "";
  const shareableJoinUrl = shareableLink
    ? `${originUrl}/join/${shareableLink.token}`
    : "";

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-t-3xl sm:rounded-3xl shadow-2xl max-w-lg w-full max-h-[90dvh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-base">
                person_add
              </span>
            </div>
            <div>
              <h3 className="font-bold text-sm text-on-surface">
                Invite colleagues to {currentWorkspace.name}
              </h3>
              <p className="text-[11px] text-outline">
                Collaborate in real-time across channels and direct messages
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close invite modal"
            className="min-w-[44px] min-h-[44px] rounded-xl text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        {/* Tab Buttons */}
        <div className="flex border-b border-outline-variant/15 px-6 gap-6 bg-surface-container-low/30 shrink-0">
          <button
            type="button"
            onClick={() => setTab("email")}
            className={`min-w-[44px] min-h-[44px] py-3 text-xs font-bold border-b-2 transition-all ${
              tab === "email"
                ? "border-primary text-primary"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Send Email Invites
          </button>
          <button
            type="button"
            onClick={() => {
              setTab("link");
              if (!shareableLink) fetchJoinLink();
            }}
            className={`min-w-[44px] min-h-[44px] py-3 text-xs font-bold border-b-2 transition-all ${
              tab === "link"
                ? "border-primary text-primary"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Shareable Join Link
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="p-6 overflow-y-auto space-y-4 flex-1 custom-scrollbar">
          {error && (
            <div className="p-3 rounded-xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
          )}
          {success && (
            <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-xs font-medium">
              {success}
            </div>
          )}

          {tab === "link" ? (
            <div className="space-y-5">
              <p className="text-xs text-outline leading-relaxed">
                Anyone with an active link can join{" "}
                <strong>{currentWorkspace.name}</strong> as a standard Member.
              </p>

              {linkLoading && !shareableLink ? (
                <div className="p-6 text-center text-outline text-xs animate-pulse">
                  Loading shareable join link...
                </div>
              ) : shareableLink ? (
                <div className="space-y-4">
                  {/* Status Indicator */}
                  <div className="flex items-center justify-between p-3 rounded-2xl bg-surface-container-low border border-outline-variant/10">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-2.5 h-2.5 rounded-full ${
                          shareableLink.status === "PENDING"
                            ? "bg-emerald-500"
                            : "bg-neutral-400"
                        }`}
                      />
                      <span className="text-xs font-bold text-on-surface">
                        {shareableLink.status === "PENDING"
                          ? "Link is Active"
                          : "Link is Deactivated"}
                      </span>
                    </div>

                    {isAdminOrOwner && (
                      <button
                        type="button"
                        onClick={handleToggleLinkActive}
                        disabled={linkLoading}
                        className={`min-w-[44px] min-h-[44px] px-3 py-1 text-xs font-bold rounded-xl transition-all ${
                          shareableLink.status === "PENDING"
                            ? "bg-error/10 text-error hover:bg-error/20"
                            : "bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20"
                        }`}
                      >
                        {shareableLink.status === "PENDING"
                          ? "Deactivate Link"
                          : "Activate Link"}
                      </button>
                    )}
                  </div>

                  {/* URL Display */}
                  <div className="space-y-2">
                    <label className="block text-[11px] font-bold text-outline uppercase tracking-wider">
                      Invite URL
                    </label>
                    <div className="flex items-center gap-2 p-2 bg-surface-container rounded-2xl border border-outline-variant/30">
                      <input
                        type="text"
                        readOnly
                        value={shareableJoinUrl}
                        className="flex-1 bg-transparent px-2 text-xs font-mono text-on-surface focus:outline-none truncate"
                      />
                      <button
                        type="button"
                        onClick={handleCopyLink}
                        disabled={shareableLink.status !== "PENDING"}
                        className="min-w-[44px] min-h-[44px] px-3 py-1.5 bg-primary text-white text-xs font-bold rounded-xl hover:bg-primary/90 transition-colors shrink-0 flex items-center gap-1.5 disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-sm">
                          {copied ? "check" : "content_copy"}
                        </span>
                        {copied ? "Copied!" : "Copy Link"}
                      </button>
                    </div>
                  </div>

                  {/* Admin Reset Control */}
                  {isAdminOrOwner && (
                    <div className="pt-2 border-t border-outline-variant/10">
                      {showResetConfirm ? (
                        <div className="p-3 rounded-2xl bg-error-container/10 border border-error/20 space-y-2">
                          <p className="text-xs text-error font-medium">
                            Resetting will permanently revoke the current link.
                            Anyone who hasn't joined will need the new link.
                          </p>
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => setShowResetConfirm(false)}
                              className="min-w-[44px] min-h-[44px] px-3 py-1 text-xs font-bold text-outline hover:text-on-surface rounded-xl"
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              onClick={handleResetLink}
                              disabled={linkLoading}
                              className="min-w-[44px] min-h-[44px] px-3 py-1 bg-error text-white text-xs font-bold rounded-xl hover:bg-error/90"
                            >
                              Confirm Reset
                            </button>
                          </div>
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setShowResetConfirm(true)}
                          className="min-w-[44px] min-h-[44px] text-xs font-bold text-outline hover:text-error transition-colors flex items-center gap-1.5"
                        >
                          <span className="material-symbols-outlined text-sm">
                            refresh
                          </span>
                          Reset Shareable Join Link
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          ) : (
            <form onSubmit={handleSendBatch} className="space-y-4">
              {/* Tokenized Multi-Email Input */}
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Recipient Email Addresses{" "}
                  <span className="text-error">*</span>
                </label>
                <div className="min-h-[70px] p-2 bg-surface-container rounded-2xl border border-outline-variant/30 focus-within:border-primary transition-colors flex flex-wrap gap-1.5 items-center">
                  {emailChips.map((chipEmail, idx) => (
                    <span
                      key={idx}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl bg-surface-container-high text-xs font-medium text-on-surface border border-outline-variant/20"
                    >
                      <span>{chipEmail}</span>
                      <button
                        type="button"
                        onClick={() => removeChip(idx)}
                        aria-label={`Remove ${chipEmail}`}
                        className="min-w-[44px] min-h-[44px] -my-3.5 -mr-3.5 text-outline hover:text-error transition-colors flex items-center justify-center"
                      >
                        <span className="material-symbols-outlined text-xs">
                          close
                        </span>
                      </button>
                    </span>
                  ))}
                  <input
                    type="text"
                    value={currentEmailInput}
                    onChange={(e) => setCurrentEmailInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    onPaste={handlePaste}
                    onBlur={() => addEmailTokens(currentEmailInput)}
                    placeholder={
                      emailChips.length === 0
                        ? "Enter emails separated by comma or space..."
                        : "Add more..."
                    }
                    className="flex-1 min-w-[140px] bg-transparent text-xs text-on-surface placeholder:text-outline outline-none px-1 py-1"
                  />
                </div>
                <span className="text-[10px] text-outline block mt-1">
                  Type or paste multiple emails separated by comma, space, or
                  enter.
                </span>
              </div>

              {/* Workspace Role Selector */}
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Assigned Workspace Role
                </label>
                <select
                  value={role}
                  onChange={(e) =>
                    setRole(e.target.value as "MEMBER" | "ADMIN")
                  }
                  disabled={!isAdminOrOwner}
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors disabled:opacity-60"
                >
                  <option value="MEMBER">Member (Standard Access)</option>
                  {isAdminOrOwner && (
                    <option value="ADMIN">
                      Admin (Can manage channels and invitations)
                    </option>
                  )}
                </select>
                {!isAdminOrOwner && (
                  <span className="text-[10px] text-outline block mt-1">
                    Standard members can invite new colleagues with the Member
                    role.
                  </span>
                )}
              </div>

              {/* Pre-assigned Initial Channels */}
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Designated Initial Channels
                </label>
                <p className="text-[11px] text-outline mb-2">
                  New members will automatically join these channels upon
                  accepting.
                </p>
                <div className="max-h-28 overflow-y-auto p-2 bg-surface-container rounded-2xl border border-outline-variant/30 space-y-1 custom-scrollbar">
                  {eligibleChannels.map((ch) => {
                    const isChecked = selectedChannelIds.includes(ch.id);
                    return (
                      <label
                        key={ch.id}
                        className="flex items-center gap-2 px-2 py-1.5 rounded-xl hover:bg-surface-container-high transition-colors cursor-pointer text-xs"
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggleChannelSelection(ch.id)}
                          className="rounded border-outline-variant/40 text-primary focus:ring-0"
                        />
                        <span className="text-outline font-bold">
                          {ch.type === "PRIVATE" ? "🔒" : "#"}
                        </span>
                        <span className="font-semibold text-on-surface truncate">
                          {ch.name}
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>

              {/* Custom Welcome Message */}
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Personalized Welcome Note{" "}
                  <span className="text-outline font-normal">(Optional)</span>
                </label>
                <textarea
                  rows={2}
                  value={customMessage}
                  onChange={(e) => setCustomMessage(e.target.value)}
                  placeholder="Welcome to our workspace! Check out the #general channel to get started."
                  className="w-full px-3.5 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors resize-none placeholder:text-outline"
                />
              </div>

              {/* Granular Batch Outcomes */}
              {batchResults && batchResults.length > 0 && (
                <div className="space-y-2 pt-2 border-t border-outline-variant/15">
                  <span className="text-[11px] font-bold text-outline uppercase tracking-wider block">
                    Invitation Outcomes
                  </span>
                  <div className="space-y-1.5 max-h-32 overflow-y-auto custom-scrollbar">
                    {batchResults.map((res, i) => {
                      let badgeColor =
                        "bg-neutral-500/10 text-neutral-600 dark:text-neutral-400";
                      let badgeText: string = res.status;
                      if (res.status === "sent") {
                        badgeColor =
                          "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400";
                        badgeText = "Sent";
                      } else if (res.status === "already_member") {
                        badgeColor =
                          "bg-amber-500/10 text-amber-600 dark:text-amber-400";
                        badgeText = "Already a Member";
                      } else if (res.status === "already_invited") {
                        badgeColor =
                          "bg-sky-500/10 text-sky-600 dark:text-sky-400";
                        badgeText = "Already Invited";
                      } else if (res.status === "invalid_email") {
                        badgeColor = "bg-error/10 text-error";
                        badgeText = "Invalid Email";
                      }

                      return (
                        <div
                          key={i}
                          className="flex items-center justify-between p-2 rounded-xl bg-surface-container-low text-xs border border-outline-variant/10"
                        >
                          <span className="font-mono text-on-surface truncate max-w-[65%]">
                            {res.email}
                          </span>
                          <span
                            className={`px-2 py-0.5 rounded-lg text-[10px] font-bold shrink-0 ${badgeColor}`}
                          >
                            {badgeText}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Submit Buttons */}
              <div className="pt-2 flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={onClose}
                  className="min-w-[44px] min-h-[44px] px-4 py-2 text-xs font-bold text-outline hover:text-on-surface hover:bg-surface-container rounded-xl transition-colors"
                >
                  Close
                </button>
                <button
                  type="submit"
                  disabled={
                    loading ||
                    (emailChips.length === 0 && !currentEmailInput.trim())
                  }
                  className="min-w-[44px] min-h-[44px] px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50 flex items-center gap-1.5"
                >
                  {loading ? (
                    "Sending..."
                  ) : (
                    <>
                      <span className="material-symbols-outlined text-sm">
                        send
                      </span>
                      <span>
                        Send Invitations
                        {emailChips.length +
                          (currentEmailInput.trim() ? 1 : 0) >
                          0 &&
                          ` (${emailChips.length + (currentEmailInput.trim() ? 1 : 0)})`}
                      </span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
