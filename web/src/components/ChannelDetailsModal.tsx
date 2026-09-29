"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  useWorkspace,
  type ChannelDto,
  type WorkspaceMemberDto,
  type BatchInvitationItemResult,
  type BatchAddChannelMembersResponse,
  MAX_CHANNEL_MEMBER_BATCH,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { parseEmailTokens } from "@/lib/emailUtils";

/** The Channel details tabs that list Members, and so have a search box. */
type SearchableTab = "members" | "add";

const EMPTY_SEARCHES: Record<SearchableTab, string> = { members: "", add: "" };

function matchesMemberSearch(member: WorkspaceMemberDto, query: string) {
  const q = query.toLowerCase();
  return (
    member.username.toLowerCase().includes(q) ||
    (member.fullName?.toLowerCase().includes(q) ?? false)
  );
}

interface ChannelDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  channel: ChannelDto;
  initialTab?: "members" | "add" | "email";
}

export default function ChannelDetailsModal({
  isOpen,
  onClose,
  channel,
  initialTab = "members",
}: ChannelDetailsModalProps) {
  const { userId } = useAuth();
  const {
    currentWorkspace,
    members: workspaceMembers,
    presenceMap,
    getChannelMembers,
    batchAddChannelMembers,
    removeChannelMember,
    leaveChannel,
    createBatchInvites,
    refreshChannels,
  } = useWorkspace();

  const [tab, setTab] = useState<"members" | "add" | "email">(initialTab);
  const [channelMembers, setChannelMembers] = useState<WorkspaceMemberDto[]>(
    []
  );
  const [loadingMembers, setLoadingMembers] = useState(false);
  // Each searchable tab keeps its own query, so one tab's search never filters another.
  const [searchByTab, setSearchByTab] = useState(EMPTY_SEARCHES);
  const setTabSearch = (searchTab: SearchableTab, query: string) =>
    setSearchByTab((prev) => ({ ...prev, [searchTab]: query }));

  // Add Members Tab State
  const [selectedUserIds, setSelectedUserIds] = useState<number[]>([]);
  const [addingMembers, setAddingMembers] = useState(false);
  const [additionResult, setAdditionResult] =
    useState<BatchAddChannelMembersResponse | null>(null);

  // Email Invite Tab State
  const [emailChips, setEmailChips] = useState<string[]>([]);
  const [currentEmailInput, setCurrentEmailInput] = useState("");
  const [inviteRole, setInviteRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
  const [customMessage, setCustomMessage] = useState("");
  const [batchResults, setBatchResults] = useState<
    BatchInvitationItemResult[] | null
  >(null);
  const [sendingInvites, setSendingInvites] = useState(false);

  // Feedback State
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  // Confirmation Prompts
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [memberToRemove, setMemberToRemove] =
    useState<WorkspaceMemberDto | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const isAdminOrOwner =
    currentWorkspace?.role === "OWNER" || currentWorkspace?.role === "ADMIN";
  const isPrivate = channel.type === "PRIVATE";

  // Fetch channel members
  const fetchChannelMembers = useCallback(async () => {
    if (!channel?.id) return;
    setLoadingMembers(true);
    try {
      const list = await getChannelMembers(channel.id);
      setChannelMembers(list || []);
    } catch (err: any) {
      console.warn("Failed to fetch channel members:", err);
    } finally {
      setLoadingMembers(false);
    }
  }, [channel?.id, getChannelMembers]);

  useEffect(() => {
    if (isOpen && channel?.id) {
      fetchChannelMembers();
      setTab(initialTab);
      setSearchByTab(EMPTY_SEARCHES);
      setSelectedUserIds([]);
      setAdditionResult(null);
      setFeedback(null);
      setShowLeaveConfirm(false);
      setMemberToRemove(null);
    }
  }, [isOpen, channel?.id, initialTab, fetchChannelMembers]);

  if (!isOpen || !channel) return null;

  // Workspace members who are NOT in the private channel
  const channelMemberUserIds = new Set(channelMembers.map((m) => m.userId));
  const eligibleNonMembers = workspaceMembers.filter(
    (m) => !channelMemberUserIds.has(m.userId)
  );

  // Filtered members for "members" tab
  const filteredChannelMembers = channelMembers.filter((m) =>
    matchesMemberSearch(m, searchByTab.members)
  );

  // Filtered eligible candidates for "add" tab
  const filteredCandidates = eligibleNonMembers.filter((m) =>
    matchesMemberSearch(m, searchByTab.add)
  );

  const overBatchLimit = selectedUserIds.length > MAX_CHANNEL_MEMBER_BATCH;

  // Workspace members by user ID, for naming the users in an addition result
  const workspaceMembersByUserId = new Map(
    workspaceMembers.map((m) => [m.userId, m])
  );
  const describeUser = (id: number) => {
    const member = workspaceMembersByUserId.get(id);
    return member ? `@${member.username}` : `User #${id}`;
  };

  // Toggle selection for candidate user
  const toggleUserSelection = (candUserId: number) => {
    setSelectedUserIds((prev) =>
      prev.includes(candUserId)
        ? prev.filter((id) => id !== candUserId)
        : [...prev, candUserId]
    );
  };

  const toggleSelectAllCandidates = () => {
    if (selectedUserIds.length === filteredCandidates.length) {
      setSelectedUserIds([]);
    } else {
      setSelectedUserIds(filteredCandidates.map((m) => m.userId));
    }
  };

  // Submit adding members
  const handleAddMembersSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedUserIds.length === 0 || overBatchLimit) return;
    setAddingMembers(true);
    setFeedback(null);
    setAdditionResult(null);

    try {
      // One or many, an addition always reports added / already in / not in Workspace.
      const result = await batchAddChannelMembers(channel.id, selectedUserIds);
      setAdditionResult(result);
      setSelectedUserIds([]);
      await fetchChannelMembers();
    } catch (err: any) {
      setFeedback({
        type: "error",
        text: err?.message || "Failed to add members to channel",
      });
    } finally {
      setAddingMembers(false);
    }
  };

  // Submit Leave Channel
  const handleConfirmLeave = async () => {
    setActionLoading(true);
    setFeedback(null);
    try {
      await leaveChannel(channel.id);
      onClose();
    } catch (err: any) {
      setFeedback({
        type: "error",
        text: err?.message || "Failed to leave channel",
      });
      setShowLeaveConfirm(false);
    } finally {
      setActionLoading(false);
    }
  };

  // Submit Remove Member
  const handleConfirmRemove = async () => {
    if (!memberToRemove) return;
    setActionLoading(true);
    setFeedback(null);
    try {
      await removeChannelMember(channel.id, memberToRemove.userId);
      setFeedback({
        type: "success",
        text: `@${memberToRemove.username} has been removed from #${channel.name}.`,
      });
      setMemberToRemove(null);
      await fetchChannelMembers();
    } catch (err: any) {
      setFeedback({
        type: "error",
        text: err?.message || "Failed to remove member",
      });
      setMemberToRemove(null);
    } finally {
      setActionLoading(false);
    }
  };

  // Email Invite Helpers
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

  const handleEmailKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
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

  const handleEmailPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
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

  const handleSendEmailInvites = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);
    setBatchResults(null);

    let allEmails = [...emailChips];
    if (currentEmailInput.trim()) {
      const extra = parseEmailTokens(currentEmailInput);
      const set = new Set([...allEmails, ...extra]);
      allEmails = Array.from(set);
    }

    if (allEmails.length === 0) {
      setFeedback({
        type: "error",
        text: "Please specify at least one recipient email address.",
      });
      return;
    }

    setSendingInvites(true);
    try {
      const response = await createBatchInvites(
        allEmails,
        inviteRole,
        customMessage.trim() || undefined,
        [channel.id]
      );

      setBatchResults(response.results);
      if (response.sentCount > 0) {
        setFeedback({
          type: "success",
          text: `Dispatched ${response.sentCount} invitation${
            response.sentCount > 1 ? "s" : ""
          } pre-assigned to #${channel.name}!`,
        });
        setEmailChips([]);
        setCurrentEmailInput("");
        setCustomMessage("");
      } else {
        setFeedback({
          type: "error",
          text: "No invitations were dispatched. Check the outcomes below.",
        });
      }
    } catch (err: any) {
      setFeedback({
        type: "error",
        text: err?.message || "Failed to send email invitations",
      });
    } finally {
      setSendingInvites(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-t-3xl sm:rounded-3xl shadow-2xl max-w-lg w-full max-h-[90dvh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary font-bold shrink-0">
              <span className="material-symbols-outlined text-base">
                {isPrivate ? "lock" : "tag"}
              </span>
            </div>
            <div className="min-w-0">
              <h3 className="font-bold text-sm text-on-surface truncate">
                #{channel.name}
              </h3>
              <p className="text-[11px] text-outline truncate">
                {channel.description ||
                  (isPrivate ? "Private Channel" : "Public Channel")}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close channel details"
            className="min-w-[44px] min-h-[44px] rounded-xl text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors shrink-0"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-outline-variant/15 px-6 gap-6 bg-surface-container-low/30 shrink-0">
          <button
            type="button"
            onClick={() => setTab("members")}
            className={`min-w-[44px] min-h-[44px] py-3 text-xs font-bold border-b-2 transition-all flex items-center gap-1.5 ${
              tab === "members"
                ? "border-primary text-primary"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            <span>Members</span>
            <span className="px-1.5 py-0.2 rounded-full bg-primary/10 text-primary text-[10px] font-bold">
              {channelMembers.length}
            </span>
          </button>

          {isPrivate && (
            <>
              <button
                type="button"
                onClick={() => setTab("add")}
                className={`min-w-[44px] min-h-[44px] py-3 text-xs font-bold border-b-2 transition-all flex items-center gap-1.5 ${
                  tab === "add"
                    ? "border-primary text-primary"
                    : "border-transparent text-outline hover:text-on-surface"
                }`}
              >
                <span>Add Members</span>
                {eligibleNonMembers.length > 0 && (
                  <span className="px-1.5 py-0.2 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[10px] font-bold">
                    {eligibleNonMembers.length}
                  </span>
                )}
              </button>

              <button
                type="button"
                onClick={() => setTab("email")}
                className={`min-w-[44px] min-h-[44px] py-3 text-xs font-bold border-b-2 transition-all ${
                  tab === "email"
                    ? "border-primary text-primary"
                    : "border-transparent text-outline hover:text-on-surface"
                }`}
              >
                Invite by Email
              </button>
            </>
          )}
        </div>

        {/* Feedback Alert */}
        {feedback && (
          <div
            className={`mx-6 mt-3 p-3 rounded-2xl text-xs font-medium border flex items-center justify-between shrink-0 ${
              feedback.type === "success"
                ? "bg-emerald-500/10 border-emerald-500/20 text-emerald-600 dark:text-emerald-400"
                : "bg-error-container/20 border-error/20 text-error"
            }`}
          >
            <span>{feedback.text}</span>
            <button
              type="button"
              onClick={() => setFeedback(null)}
              aria-label="Dismiss message"
              className="min-w-[44px] min-h-[44px] -my-3 -mr-2 flex items-center justify-center shrink-0 rounded-xl text-xs opacity-70 hover:opacity-100"
            >
              ✕
            </button>
          </div>
        )}

        {/* Confirmation Overlays */}
        {showLeaveConfirm && (
          <div className="m-4 p-4 rounded-2xl bg-error-container/15 border border-error/20 space-y-3 shrink-0">
            <h4 className="font-bold text-xs text-error">
              Leave #{channel.name}?
            </h4>
            <p className="text-xs text-outline leading-relaxed">
              You will lose access to the conversation history and will not
              receive new messages unless a member adds you back.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowLeaveConfirm(false)}
                className="min-w-[44px] min-h-[44px] px-3 py-1.5 text-xs font-bold text-outline hover:text-on-surface rounded-xl"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmLeave}
                disabled={actionLoading}
                className="min-w-[44px] min-h-[44px] px-4 py-1.5 bg-error text-white text-xs font-bold rounded-xl hover:bg-error/90 transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Leaving..." : "Confirm Leave"}
              </button>
            </div>
          </div>
        )}

        {memberToRemove && (
          <div className="m-4 p-4 rounded-2xl bg-error-container/15 border border-error/20 space-y-3 shrink-0">
            <h4 className="font-bold text-xs text-error">
              Remove @{memberToRemove.username} from #{channel.name}?
            </h4>
            <p className="text-xs text-outline leading-relaxed">
              They will immediately lose access to this private channel and its
              messages.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setMemberToRemove(null)}
                className="min-w-[44px] min-h-[44px] px-3 py-1.5 text-xs font-bold text-outline hover:text-on-surface rounded-xl"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmRemove}
                disabled={actionLoading}
                className="min-w-[44px] min-h-[44px] px-4 py-1.5 bg-error text-white text-xs font-bold rounded-xl hover:bg-error/90 transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Removing..." : "Confirm Removal"}
              </button>
            </div>
          </div>
        )}

        {/* Tab 1: Current Channel Members */}
        {tab === "members" && (
          <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
            {/* Search */}
            <div className="p-4 border-b border-outline-variant/15 bg-surface-container-low/20 shrink-0">
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-base">
                  search
                </span>
                <input
                  type="text"
                  placeholder="Search channel members..."
                  value={searchByTab.members}
                  onChange={(e) => setTabSearch("members", e.target.value)}
                  className="w-full pl-9 pr-3 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors"
                />
              </div>
            </div>

            {/* List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-2 custom-scrollbar">
              {loadingMembers ? (
                <div className="text-center py-8 text-outline text-xs animate-pulse">
                  Loading channel members...
                </div>
              ) : filteredChannelMembers.length === 0 ? (
                <div className="text-center py-8 text-outline text-xs">
                  No channel members found.
                </div>
              ) : (
                filteredChannelMembers.map((member) => {
                  const isSelf = member.userId === userId;
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
                          <div className="flex items-center gap-1.5">
                            <span className="font-bold text-xs text-on-surface truncate">
                              {member.fullName || member.username}
                            </span>
                            {isSelf && (
                              <span className="text-[10px] px-1.5 py-0.2 rounded bg-primary/10 text-primary font-bold">
                                you
                              </span>
                            )}
                          </div>
                          <span className="text-[11px] text-outline block truncate">
                            @{member.username} •{" "}
                            <span className="capitalize">
                              {member.role.toLowerCase()}
                            </span>
                          </span>
                        </div>
                      </div>

                      {/* Actions */}
                      <div className="flex items-center gap-1.5 shrink-0">
                        {isPrivate && isSelf && (
                          <button
                            type="button"
                            onClick={() => setShowLeaveConfirm(true)}
                            className="min-w-[44px] min-h-[44px] px-3 py-1.5 rounded-xl bg-error/10 text-error hover:bg-error/20 text-xs font-bold transition-colors flex items-center gap-1"
                          >
                            <span className="material-symbols-outlined text-sm">
                              logout
                            </span>
                            Leave
                          </button>
                        )}

                        {isPrivate && !isSelf && isAdminOrOwner && (
                          <button
                            type="button"
                            onClick={() => setMemberToRemove(member)}
                            title={`Remove @${member.username} from channel`}
                            className="min-w-[44px] min-h-[44px] rounded-xl bg-surface-container-high hover:bg-error/10 text-outline hover:text-error flex items-center justify-center transition-colors"
                          >
                            <span className="material-symbols-outlined text-base">
                              person_remove
                            </span>
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}

        {/* Tab 2: Add Workspace Members */}
        {tab === "add" && isPrivate && (
          <form
            onSubmit={handleAddMembersSubmit}
            className="flex-1 flex flex-col min-h-0 overflow-hidden"
          >
            {additionResult && (
              <AdditionResultPanel
                result={additionResult}
                channelName={channel.name}
                describeUser={describeUser}
                onDismiss={() => setAdditionResult(null)}
              />
            )}

            {/* Search & Select All Header */}
            <div className="p-4 border-b border-outline-variant/15 bg-surface-container-low/20 space-y-2.5 shrink-0">
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-base">
                  search
                </span>
                <input
                  type="text"
                  placeholder="Search colleagues to add..."
                  value={searchByTab.add}
                  onChange={(e) => setTabSearch("add", e.target.value)}
                  className="w-full pl-9 pr-3 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors"
                />
              </div>

              {filteredCandidates.length > 0 && (
                <div className="flex items-center justify-between text-xs px-1">
                  <button
                    type="button"
                    onClick={toggleSelectAllCandidates}
                    className="min-w-[44px] min-h-[44px] -my-2 text-primary font-bold hover:underline text-[11px]"
                  >
                    {selectedUserIds.length === filteredCandidates.length
                      ? "Deselect All"
                      : "Select All"}
                  </button>
                  <span className="text-[11px] text-outline">
                    {selectedUserIds.length} of {filteredCandidates.length}{" "}
                    selected
                  </span>
                </div>
              )}
            </div>

            {/* Candidate List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-2 custom-scrollbar">
              {eligibleNonMembers.length === 0 ? (
                <div className="text-center py-10 space-y-2">
                  <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 mx-auto flex items-center justify-center">
                    <span className="material-symbols-outlined text-2xl">
                      done_all
                    </span>
                  </div>
                  <h4 className="text-xs font-bold text-on-surface">
                    All colleagues are in this channel
                  </h4>
                  <p className="text-[11px] text-outline max-w-xs mx-auto">
                    Every member of {currentWorkspace?.name} already has access
                    to #{channel.name}.
                  </p>
                </div>
              ) : filteredCandidates.length === 0 ? (
                <div className="text-center py-8 text-outline text-xs">
                  No colleagues match your search.
                </div>
              ) : (
                filteredCandidates.map((cand) => {
                  const isSelected = selectedUserIds.includes(cand.userId);
                  const isOnline = presenceMap[cand.userId] === "ONLINE";

                  return (
                    <label
                      key={cand.id}
                      className={`flex items-center justify-between p-3 rounded-2xl border transition-colors cursor-pointer select-none ${
                        isSelected
                          ? "bg-primary/5 border-primary/30"
                          : "bg-surface-container-low hover:bg-surface-container border-outline-variant/10"
                      }`}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleUserSelection(cand.userId)}
                          className="rounded border-outline-variant/40 text-primary focus:ring-0 w-4 h-4"
                        />
                        <div className="relative shrink-0">
                          <div className="w-9 h-9 rounded-xl bg-primary/10 flex items-center justify-center font-bold text-primary text-xs">
                            {cand.username.charAt(0).toUpperCase()}
                          </div>
                          <span
                            className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full ring-2 ring-surface-container-lowest ${
                              isOnline ? "bg-emerald-500" : "bg-neutral-400"
                            }`}
                          />
                        </div>
                        <div className="min-w-0">
                          <span className="font-bold text-xs text-on-surface block truncate">
                            {cand.fullName || cand.username}
                          </span>
                          <span className="text-[11px] text-outline block truncate">
                            @{cand.username} •{" "}
                            <span className="capitalize">
                              {cand.role.toLowerCase()}
                            </span>
                          </span>
                        </div>
                      </div>
                    </label>
                  );
                })
              )}
            </div>

            {/* Bottom Action Bar */}
            {eligibleNonMembers.length > 0 && overBatchLimit && (
              <p
                role="alert"
                className="mx-4 mt-3 p-3 rounded-2xl text-xs font-medium border bg-error-container/20 border-error/20 text-error shrink-0"
              >
                You can add at most {MAX_CHANNEL_MEMBER_BATCH} colleagues at a
                time. Deselect{" "}
                {selectedUserIds.length - MAX_CHANNEL_MEMBER_BATCH} to continue.
              </p>
            )}
            {eligibleNonMembers.length > 0 && (
              <div className="p-4 border-t border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between shrink-0">
                <span className="text-xs text-outline font-semibold">
                  {selectedUserIds.length} selected
                </span>
                <button
                  type="submit"
                  disabled={
                    addingMembers ||
                    selectedUserIds.length === 0 ||
                    overBatchLimit
                  }
                  className="min-w-[44px] min-h-[44px] px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50 flex items-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-sm">
                    person_add
                  </span>
                  <span>
                    {addingMembers
                      ? "Adding..."
                      : `Add to Channel (${selectedUserIds.length})`}
                  </span>
                </button>
              </div>
            )}
          </form>
        )}

        {/* Tab 3: Invite by Email */}
        {tab === "email" && isPrivate && (
          <form
            onSubmit={handleSendEmailInvites}
            className="p-6 overflow-y-auto space-y-4 flex-1 custom-scrollbar"
          >
            <p className="text-xs text-outline leading-relaxed">
              Invite colleagues directly by email. When they accept, they will
              automatically join this workspace and{" "}
              <strong>#{channel.name}</strong>.
            </p>

            {/* Email Token Input */}
            <div>
              <label className="block text-xs font-bold text-on-surface mb-1.5">
                Recipient Email Addresses <span className="text-error">*</span>
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
                      onClick={() =>
                        setEmailChips((prev) =>
                          prev.filter((_, i) => i !== idx)
                        )
                      }
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
                  onKeyDown={handleEmailKeyDown}
                  onPaste={handleEmailPaste}
                  onBlur={() => addEmailTokens(currentEmailInput)}
                  placeholder={
                    emailChips.length === 0
                      ? "Enter emails separated by comma or space..."
                      : "Add more..."
                  }
                  className="flex-1 min-w-[140px] bg-transparent text-xs text-on-surface placeholder:text-outline outline-none px-1 py-1"
                />
              </div>
            </div>

            {/* Workspace Role Selector */}
            <div>
              <label className="block text-xs font-bold text-on-surface mb-1.5">
                Assigned Workspace Role
              </label>
              <select
                value={inviteRole}
                onChange={(e) =>
                  setInviteRole(e.target.value as "MEMBER" | "ADMIN")
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
            </div>

            {/* Pre-assigned Channel Pill */}
            <div>
              <label className="block text-xs font-bold text-on-surface mb-1.5">
                Pre-assigned Channel
              </label>
              <div className="flex items-center gap-2 p-2.5 rounded-xl bg-surface-container-high/50 border border-outline-variant/20">
                <span className="material-symbols-outlined text-primary text-sm">
                  lock
                </span>
                <span className="text-xs font-bold text-on-surface">
                  #{channel.name}
                </span>
                <span className="text-[10px] text-outline ml-auto">
                  Auto-joins on acceptance
                </span>
              </div>
            </div>

            {/* Custom Note */}
            <div>
              <label className="block text-xs font-bold text-on-surface mb-1.5">
                Welcome Note{" "}
                <span className="text-outline font-normal">(Optional)</span>
              </label>
              <textarea
                rows={2}
                value={customMessage}
                onChange={(e) => setCustomMessage(e.target.value)}
                placeholder={`Hey, join us in #${channel.name}!`}
                className="w-full px-3.5 py-2 bg-surface-container rounded-xl border border-outline-variant/30 text-xs focus:outline-none focus:border-primary transition-colors resize-none placeholder:text-outline"
              />
            </div>

            {/* Outcomes */}
            {batchResults && batchResults.length > 0 && (
              <div className="space-y-1.5 max-h-32 overflow-y-auto custom-scrollbar pt-2 border-t border-outline-variant/15">
                {batchResults.map((res, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-2 rounded-xl bg-surface-container-low text-xs border border-outline-variant/10"
                  >
                    <span className="font-mono text-on-surface truncate max-w-[65%]">
                      {res.email}
                    </span>
                    <span
                      className={`px-2 py-0.5 rounded-lg text-[10px] font-bold shrink-0 ${
                        res.status === "sent"
                          ? "bg-emerald-500/10 text-emerald-600"
                          : "bg-error/10 text-error"
                      }`}
                    >
                      {res.status}
                    </span>
                  </div>
                ))}
              </div>
            )}

            {/* Actions */}
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
                  sendingInvites ||
                  (emailChips.length === 0 && !currentEmailInput.trim())
                }
                className="min-w-[44px] min-h-[44px] px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50 flex items-center gap-1.5"
              >
                {sendingInvites ? "Sending..." : "Send Invitations"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

interface AdditionResultPanelProps {
  result: BatchAddChannelMembersResponse;
  channelName: string;
  describeUser: (userId: number) => string;
  onDismiss: () => void;
}

/** The categorized outcome of a Channel Member addition, shown on the Add Members tab. */
function AdditionResultPanel({
  result,
  channelName,
  describeUser,
  onDismiss,
}: AdditionResultPanelProps) {
  const groups = [
    {
      label: "Added",
      ids: result.added,
      tone: "text-emerald-600 dark:text-emerald-400",
      hint: null,
    },
    {
      label: "Already in the channel",
      ids: result.alreadyMembers,
      tone: "text-outline",
      hint: null,
    },
    {
      label: "Not in this Workspace",
      ids: result.notWorkspaceMembers,
      tone: "text-error",
      hint: "Invite them by email instead.",
    },
  ].filter((g) => g.ids.length > 0);

  return (
    <div
      role="status"
      aria-label="Add members result"
      className="mx-4 mt-3 p-3 rounded-2xl border border-outline-variant/20 bg-surface-container-low text-xs space-y-2 shrink-0"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-bold text-on-surface">
          {result.added.length > 0
            ? `Added ${result.added.length} colleague${
                result.added.length > 1 ? "s" : ""
              } to #${channelName}.`
            : "No one was added."}
        </p>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss add members result"
          className="min-w-[44px] min-h-[44px] -my-3 -mr-2 flex items-center justify-center shrink-0 rounded-xl text-xs opacity-70 hover:opacity-100"
        >
          ✕
        </button>
      </div>
      {groups.map((group) => (
        <div key={group.label}>
          <p className={`font-semibold ${group.tone}`}>
            {group.label} ({group.ids.length})
          </p>
          <ul aria-label={group.label} className="flex flex-wrap gap-1.5 mt-1">
            {group.ids.map((id) => (
              <li
                key={id}
                className="px-2 py-0.5 rounded-lg bg-surface-container text-on-surface"
              >
                {describeUser(id)}
              </li>
            ))}
          </ul>
          {group.hint && (
            <p className="text-[11px] text-outline mt-1">{group.hint}</p>
          )}
        </div>
      ))}
    </div>
  );
}
