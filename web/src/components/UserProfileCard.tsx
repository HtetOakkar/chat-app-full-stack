"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { usePresence } from "@/context/PresenceContext";
import { useTranslations } from "@/lib/i18n";

interface UserProfileCardProps {
  userId: number | null;
  fallbackUsername?: string;
  currentUserId?: number | null;
  contacts?: any[];
  requests?: any[];
  onAddContact?: (username: string, id: number) => Promise<void>;
  onAcceptRequest?: (id: number) => Promise<void>;
  onSelectChat?: (chat: any) => void;
  userStatus?: string;
  onBack: () => void;
}

interface ProfileData {
  username: string;
  fullName: string | null;
}

export default function UserProfileCard({
  userId,
  fallbackUsername,
  currentUserId,
  contacts = [],
  requests = [],
  onAddContact,
  onAcceptRequest,
  onSelectChat,
  userStatus,
  onBack,
}: UserProfileCardProps) {
  const t = useTranslations();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const { onlineUsers } = usePresence();

  useEffect(() => {
    if (userId) {
      const fetchProfile = async () => {
        try {
          setLoading(true);
          setError("");
          const data = await apiFetch(`/api/v1/users/${userId}/profile`);
          if (data) {
            setProfile(data);
          }
        } catch (err: unknown) {
          setError(t.failedLoadUserProfile);
        } finally {
          setLoading(false);
        }
      };
      fetchProfile();
    } else {
      setProfile(null);
    }
  }, [userId]);

  const avatarLetter = (
    profile?.fullName ||
    profile?.username ||
    fallbackUsername ||
    "C"
  )
    .charAt(0)
    .toUpperCase();

  const isSelf = userId === currentUserId;
  const isContact = contacts?.find((c) => c.contactUserId === userId);
  const isPending = requests?.find((r) => r.contactUserId === userId);
  const isAcceptedContact =
    (isContact &&
      (isContact.status === "ACCEPTED" || isContact.status === "CONTACT")) ||
    userStatus === "ACCEPTED" ||
    userStatus === "CONTACT";
  const isBlocked =
    (isContact && isContact.status === "BLOCKED") || userStatus === "BLOCKED";

  return (
    <div className="flex-1 flex justify-center overflow-y-auto bg-background custom-scrollbar w-full">
      <div className="w-full max-w-2xl px-4 py-6 md:py-10">
        {/* Back Button */}
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-outline hover:text-on-surface mb-6 transition-colors"
        >
          <span className="material-symbols-outlined text-lg">arrow_back</span>
          <span className="text-sm font-medium">{t.backToChat}</span>
        </button>

        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3">
            <span className="material-symbols-outlined animate-spin text-primary text-3xl">
              rotate_right
            </span>
            <span className="text-sm text-outline font-bold uppercase tracking-wider">
              {t.loadingProfile}
            </span>
          </div>
        ) : error ? (
          <div className="bg-error-container text-on-error-container text-xs p-3 rounded-lg flex items-center gap-2 border border-error/10">
            <span className="material-symbols-outlined text-error text-base">
              error
            </span>
            {error}
          </div>
        ) : (
          <div className="bg-surface-container-lowest outline outline-1 outline-outline-variant/30 rounded-2xl shadow-[0_24px_64px_rgba(0,66,117,0.06)] overflow-hidden flex flex-col">
            {/* Cover Banner */}
            <div className="h-32 bg-gradient-to-r from-primary/20 via-primary/10 to-transparent relative">
              <div className="absolute inset-0 bg-gradient-to-t from-surface-container-lowest/80 to-transparent" />
            </div>

            {/* Profile Info Section with Overlapping Avatar */}
            <div className="px-6 pb-6 relative">
              <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 -mt-12 mb-6">
                <div className="w-24 h-24 rounded-2xl bg-surface-container-lowest p-1 shadow-lg border border-outline-variant/20 shrink-0 relative">
                  <div className="w-full h-full rounded-xl bg-primary/10 flex items-center justify-center text-primary font-black text-3xl">
                    {avatarLetter}
                  </div>
                  {/* Online indicator */}
                  <div
                    className={`absolute -bottom-1 -right-1 w-5 h-5 border-4 border-surface-container-lowest rounded-full ${
                      userId && onlineUsers[userId]?.status === "ONLINE"
                        ? "bg-tertiary"
                        : "bg-outline/30"
                    }`}
                  />
                </div>
                <div className="flex-1 text-center sm:text-left mt-2 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-bold text-on-surface leading-tight">
                      {profile?.fullName || t.noNameSpecified}
                    </h2>
                    <p className="text-sm font-medium text-outline mt-1">
                      @{profile?.username || fallbackUsername}
                    </p>
                  </div>
                  <div className="flex-shrink-0">
                    {/* Connection Status and Actions */}
                    {(() => {
                      if (isSelf || !userId) return null;

                      if (isBlocked) {
                        return (
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={async () => {
                              setActionLoading(true);
                              try {
                                await apiFetch(
                                  `/api/v1/contacts/${userId}/unblock`,
                                  { method: "PUT" }
                                );
                                window.dispatchEvent(
                                  new CustomEvent("contacts:updated")
                                );
                                onBack();
                              } catch {
                                // handle silently
                              } finally {
                                setActionLoading(false);
                              }
                            }}
                            className="flex items-center justify-center gap-2 px-6 py-2 bg-error/10 text-error text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-error hover:text-white transition-all border border-error/15 shadow-sm active:scale-98 disabled:opacity-50 cursor-pointer"
                          >
                            {actionLoading ? (
                              <span className="material-symbols-outlined animate-spin text-sm">
                                rotate_right
                              </span>
                            ) : (
                              <span className="material-symbols-outlined text-sm">
                                lock_open
                              </span>
                            )}
                            {t.unblock}
                          </button>
                        );
                      } else if (isAcceptedContact) {
                        return (
                          <button
                            type="button"
                            onClick={() => {
                              if (onSelectChat) {
                                onSelectChat({
                                  id: userId,
                                  username:
                                    profile?.username || fallbackUsername || "",
                                  isPublic: false,
                                  status: isContact?.status || userStatus,
                                });
                              }
                            }}
                            className="flex items-center justify-center gap-2 px-6 py-2 bg-primary text-white text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-primary/90 transition-all shadow-md active:scale-98 cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-sm">
                              chat
                            </span>
                            {t.message}
                          </button>
                        );
                      } else if (isPending) {
                        return (
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={async () => {
                              setActionLoading(true);
                              try {
                                if (onAcceptRequest) {
                                  await onAcceptRequest(userId);
                                } else {
                                  await apiFetch(
                                    `/api/v1/contacts/${userId}/accept`,
                                    { method: "PUT" }
                                  );
                                  window.dispatchEvent(
                                    new CustomEvent("contacts:updated")
                                  );
                                }
                                if (onSelectChat) {
                                  onSelectChat({
                                    id: userId,
                                    username:
                                      profile?.username ||
                                      fallbackUsername ||
                                      "",
                                    isPublic: false,
                                    status: "ACCEPTED",
                                  });
                                }
                              } catch {
                                // handle silently
                              } finally {
                                setActionLoading(false);
                              }
                            }}
                            className="flex items-center justify-center gap-2 px-6 py-2 bg-secondary text-white text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-secondary/90 transition-all shadow-md active:scale-98 disabled:opacity-50 cursor-pointer"
                          >
                            {actionLoading ? (
                              <span className="material-symbols-outlined animate-spin text-sm">
                                rotate_right
                              </span>
                            ) : (
                              <span className="material-symbols-outlined text-sm">
                                person_add
                              </span>
                            )}
                            {t.accept}
                          </button>
                        );
                      } else {
                        return (
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={async () => {
                              setActionLoading(true);
                              try {
                                if (onAddContact) {
                                  await onAddContact(
                                    profile?.username || fallbackUsername || "",
                                    userId
                                  );
                                } else {
                                  await apiFetch("/api/v1/contacts", {
                                    method: "POST",
                                    body: JSON.stringify({
                                      username:
                                        profile?.username ||
                                        fallbackUsername ||
                                        "",
                                    }),
                                  });
                                  window.dispatchEvent(
                                    new CustomEvent("contacts:updated")
                                  );
                                }
                                if (onSelectChat) {
                                  onSelectChat({
                                    id: userId,
                                    username:
                                      profile?.username ||
                                      fallbackUsername ||
                                      "",
                                    isPublic: false,
                                    status: "CONTACT",
                                  });
                                }
                              } catch {
                                // handle silently
                              } finally {
                                setActionLoading(false);
                              }
                            }}
                            className="flex items-center justify-center gap-2 px-6 py-2 bg-primary/10 text-primary text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-primary hover:text-white transition-all border border-primary/15 shadow-sm active:scale-98 disabled:opacity-50 cursor-pointer"
                          >
                            {actionLoading ? (
                              <span className="material-symbols-outlined animate-spin text-sm">
                                rotate_right
                              </span>
                            ) : (
                              <span className="material-symbols-outlined text-sm">
                                person_add
                              </span>
                            )}
                            {t.add}
                          </button>
                        );
                      }
                    })()}
                  </div>
                </div>
              </div>

              {/* Details List */}
              <div className="space-y-4 pt-4 border-t border-outline-variant/10">
                {/* Username */}
                <div>
                  <span className="block text-[9px] font-bold uppercase tracking-widest text-outline mb-1">
                    {t.profileUsername}
                  </span>
                  <div className="bg-surface-container-low/30 border border-outline-variant/10 rounded-lg p-3 text-xs text-on-surface font-semibold">
                    @{profile?.username || fallbackUsername}
                  </div>
                </div>
              </div>

              {/* Actions List (Block, Delete, Unblock) */}
              {isBlocked && (
                <div className="mt-4 pt-4 border-t border-outline-variant/10 flex flex-col gap-1">
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={async () => {
                      if (!userId) return;
                      setActionLoading(true);
                      try {
                        await apiFetch(`/api/v1/contacts/${userId}/unblock`, {
                          method: "PUT",
                        });
                        window.dispatchEvent(
                          new CustomEvent("contacts:updated")
                        );
                        onBack();
                      } catch {
                        // Silent fail
                      } finally {
                        setActionLoading(false);
                      }
                    }}
                    className="w-full text-left px-3 py-2.5 rounded-lg text-xs font-bold text-on-surface hover:bg-surface-container-low transition-colors flex items-center gap-2 cursor-pointer disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-sm">
                      lock_open
                    </span>
                    {t.unblock}
                  </button>
                </div>
              )}
              {isAcceptedContact && (
                <div className="mt-4 pt-4 border-t border-outline-variant/10 flex flex-col gap-1">
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={async () => {
                      if (!userId) return;
                      if (!window.confirm(t.blockUserConfirm)) return;
                      setActionLoading(true);
                      try {
                        await apiFetch(`/api/v1/contacts/${userId}/block`, {
                          method: "PUT",
                        });
                        window.dispatchEvent(
                          new CustomEvent("contacts:updated")
                        );
                        window.dispatchEvent(
                          new CustomEvent("chat:deleted", {
                            detail: { contactUserId: userId },
                          })
                        );
                        onBack();
                      } catch {
                        // Silent fail
                      } finally {
                        setActionLoading(false);
                      }
                    }}
                    className="w-full text-left px-3 py-2.5 rounded-lg text-xs font-bold text-on-surface hover:bg-surface-container-low transition-colors flex items-center gap-2 cursor-pointer disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-sm">
                      block
                    </span>
                    {t.block}
                  </button>
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={async () => {
                      if (!userId) return;
                      if (!window.confirm(t.removeContactConfirm)) {
                        return;
                      }
                      setActionLoading(true);
                      try {
                        await apiFetch(`/api/v1/contacts/${userId}`, {
                          method: "DELETE",
                        });
                        window.dispatchEvent(
                          new CustomEvent("contacts:updated")
                        );
                        window.dispatchEvent(
                          new CustomEvent("chat:deleted", {
                            detail: { contactUserId: userId },
                          })
                        );
                        onBack();
                      } catch {
                        // Silent fail
                      } finally {
                        setActionLoading(false);
                      }
                    }}
                    className="w-full text-left px-3 py-2.5 rounded-lg text-xs font-bold text-error hover:bg-error-container/30 transition-colors flex items-center gap-2 cursor-pointer disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-sm">
                      person_remove
                    </span>
                    {t.deleteContact}
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
