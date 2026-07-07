"use client";

import { useEffect, useRef, useState, Fragment, useCallback } from "react";
import React from "react";
import { useConnection } from "@/context/ConnectionContext";
import { useMessageStore } from "@/context/MessageStore";
import { usePresence } from "@/context/PresenceContext";
import { useAuth } from "@/context/AuthContext";
import { Virtuoso } from "react-virtuoso";
import { apiFetch } from "@/lib/api";
import type { ActiveChat } from "./Sidebar";
import EmojiPicker, { Theme, EmojiStyle } from "emoji-picker-react";
import { useCall } from "@/context/CallContext";

const formatDateHeader = (timestampString: string) => {
  const date = new Date(timestampString);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (date.toDateString() === today.toDateString()) {
    return "Today";
  } else if (date.toDateString() === yesterday.toDateString()) {
    return "Yesterday";
  } else {
    return date.toLocaleDateString(undefined, {
      weekday: "long",
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  }
};

const VirtuosoList = React.forwardRef<HTMLDivElement, any>(
  ({ children, style, ...props }, ref) => (
    <div
      {...props}
      ref={ref}
      style={{ ...style }}
      className="p-6 flex flex-col gap-3"
    >
      {children}
    </div>
  )
);
VirtuosoList.displayName = "VirtuosoList";

interface ChatViewportProps {
  activeChat: ActiveChat | null;
  onBannerAction: () => void;
  onBackToList?: () => void;
  onViewUserProfile?: (user: {
    id: number;
    username: string;
    status?: string;
  }) => void;
}

export default function ChatViewport({
  activeChat,
  onBannerAction,
  onBackToList,
  onViewUserProfile,
}: ChatViewportProps) {
  const { connected } = useConnection();
  const {
    publicMessages,
    privateMessages,
    sendPublicMessage,
    sendPrivateMessage,
    loadPublicHistory,
    loadPrivateHistory,
    hasMorePublicHistory,
    hasMorePrivateHistory,
  } = useMessageStore();
  const { onlineUsers, typingUsers, sendTypingIndicator } = usePresence();
  const { initiateCall } = useCall();

  const { userId } = useAuth();
  const [inputText, setInputText] = useState("");
  const [bannerLoading, setBannerLoading] = useState<string | null>(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [activeMenuMessageId, setActiveMenuMessageId] = useState<number | null>(
    null
  );
  const [showHeaderMenu, setShowHeaderMenu] = useState(false);

  const handleDeleteChat = async (contactUserId: number) => {
    if (
      !confirm(
        "Are you sure you want to delete this chat? This will clear the conversation history for you. The other user will not be notified."
      )
    ) {
      return;
    }
    try {
      await apiFetch(`/api/v1/messages/private/${contactUserId}`, {
        method: "DELETE",
      });
      window.dispatchEvent(
        new CustomEvent("chat:deleted", { detail: { contactUserId } })
      );
    } catch (err) {
      console.error("Failed to delete chat:", err);
    }
  };

  const handleDeleteMessage = async (messageId: number, timestamp: string) => {
    try {
      const url = `/api/v1/messages/${messageId}?timestamp=${encodeURIComponent(timestamp)}`;
      await apiFetch(url, {
        method: "DELETE",
      });
    } catch (err) {
      console.error("Failed to delete message:", err);
    } finally {
      setActiveMenuMessageId(null);
    }
  };

  // Close header settings menu on outside clicks
  useEffect(() => {
    const handleWindowClick = () => {
      setShowHeaderMenu(false);
    };
    if (showHeaderMenu) {
      window.addEventListener("click", handleWindowClick);
    }
    return () => {
      window.removeEventListener("click", handleWindowClick);
    };
  }, [showHeaderMenu]);

  // Close message dropdown menu on outside clicks
  useEffect(() => {
    const handleWindowClick = () => {
      setActiveMenuMessageId(null);
    };
    if (activeMenuMessageId !== null) {
      window.addEventListener("click", handleWindowClick);
    }
    return () => {
      window.removeEventListener("click", handleWindowClick);
    };
  }, [activeMenuMessageId]);

  const emojiPickerRef = useRef<HTMLDivElement | null>(null);

  // Close emoji picker when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        emojiPickerRef.current &&
        !emojiPickerRef.current.contains(event.target as Node)
      ) {
        setShowEmojiPicker(false);
      }
    };
    if (showEmojiPicker) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [showEmojiPicker]);

  // Load history when changing chats
  useEffect(() => {
    if (!activeChat) return;
    if (activeChat.isPublic) {
      loadPublicHistory();
    } else {
      loadPrivateHistory(activeChat.id);
      apiFetch(`/api/v1/messages/read/${activeChat.id}`, {
        method: "PUT",
      }).catch(() => {});
    }
  }, [activeChat?.id, activeChat?.isPublic]);

  // Mark incoming active chat messages as read immediately
  useEffect(() => {
    if (!activeChat || activeChat.isPublic) return;

    const handleMessageReceived = (event: Event) => {
      const customEvent = event as CustomEvent;
      const msg = customEvent.detail;
      if (!msg) return;

      if (msg.senderId === activeChat.id) {
        apiFetch(`/api/v1/messages/read/${activeChat.id}`, {
          method: "PUT",
        }).catch(() => {});
      }
    };

    window.addEventListener("message:received", handleMessageReceived);
    return () => {
      window.removeEventListener("message:received", handleMessageReceived);
    };
  }, [activeChat?.id, activeChat?.isPublic]);

  // Start-reached (scroll-up) history loading
  const handleStartReached = useCallback(async () => {
    if (!activeChat) return;

    if (activeChat.isPublic) {
      if (publicMessages.length > 0 && hasMorePublicHistory) {
        await loadPublicHistory();
      }
    } else {
      if (
        privateMessages[activeChat.id]?.length > 0 &&
        hasMorePrivateHistory[activeChat.id] !== false
      ) {
        await loadPrivateHistory(activeChat.id);
      }
    }
  }, [
    activeChat,
    publicMessages.length,
    hasMorePublicHistory,
    loadPublicHistory,
    privateMessages,
    hasMorePrivateHistory,
    loadPrivateHistory,
  ]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || !activeChat) return;

    const isPendingOrNeglected =
      activeChat.status === "PENDING_REQUEST" ||
      activeChat.status === "NEGLECTED";

    if (activeChat.isPublic) {
      sendPublicMessage(inputText.trim());
    } else {
      sendPrivateMessage(activeChat.id, inputText.trim());
      sendTypingIndicator(activeChat.id, false);
    }
    setInputText("");

    if (isPendingOrNeglected) {
      try {
        await apiFetch(`/api/v1/contacts/${activeChat.id}/accept`, {
          method: "PUT",
        });
      } catch {
        // Silent fail
      }
      onBannerAction();
    }
  };

  const handleBannerAction = async (action: "accept" | "neglect" | "block") => {
    if (!activeChat) return;
    setBannerLoading(action);
    try {
      await apiFetch(`/api/v1/contacts/${activeChat.id}/${action}`, {
        method: "PUT",
      });
      onBannerAction();
    } catch {
      // Silent fail
    } finally {
      setBannerLoading(null);
    }
  };

  const currentMessages = activeChat
    ? activeChat.isPublic
      ? publicMessages
      : privateMessages[activeChat.id] || []
    : [];

  const renderItem = useCallback(
    (index: number, msg: any) => {
      const isOwnMessage = msg.senderId === userId;

      // Date grouping/separator logic
      const msgDate = new Date(msg.timestamp).toDateString();
      const arrayIndex = index - (10000 - currentMessages.length);
      const prevMsg = arrayIndex > 0 ? currentMessages[arrayIndex - 1] : null;
      const prevMsgDate = prevMsg
        ? new Date(prevMsg.timestamp).toDateString()
        : null;
      const showDateSeparator = msgDate !== prevMsgDate;

      const isCallRecord =
        msg.messageType === "AUDIO" || msg.messageType === "VIDEO";

      // Build human-friendly Call Record content
      const renderCallRecord = () => {
        const outcome = msg.callOutcome || "completed";
        const duration = msg.callDuration || 0;
        const isVideo = msg.messageType === "VIDEO";

        const outcomeLabels: Record<string, string> = {
          completed: "Call Ended",
          missed: "Missed Call",
          rejected: "Call Declined",
          cancelled: "Call Cancelled",
        };
        const label = outcomeLabels[outcome] || "Call";

        const formatCallDuration = (secs: number) => {
          if (secs <= 0) return "";
          const m = Math.floor(secs / 60)
            .toString()
            .padStart(2, "0");
          const s = (secs % 60).toString().padStart(2, "0");
          return `${m}:${s}`;
        };

        const durationText = formatCallDuration(duration);

        return (
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-sm">
              {isVideo ? "videocam" : "call"}
            </span>
            <div className="flex flex-col">
              <span className="font-bold text-[10px] uppercase tracking-wider">
                {label}
              </span>
              {durationText && (
                <span className="text-[9px] opacity-70">{durationText}</span>
              )}
            </div>
          </div>
        );
      };

      return (
        <div key={msg.id || `msg-${index}`} className="flex flex-col w-full">
          {showDateSeparator && (
            <div className="flex items-center justify-center my-4 w-full gap-4">
              <div className="h-px bg-outline-variant/20 flex-1" />
              <span className="text-[9px] font-bold uppercase tracking-widest text-outline bg-surface-container-low px-3 py-1 rounded-full">
                {formatDateHeader(msg.timestamp)}
              </span>
              <div className="h-px bg-outline-variant/20 flex-1" />
            </div>
          )}

          {isCallRecord ? (
            /* Call Record bubble — centered system-style message */
            <div className="flex justify-center my-2">
              <div className="inline-flex items-center gap-2 px-4 py-2.5 rounded-full bg-surface-container-low border border-outline-variant/15 text-on-surface">
                {renderCallRecord()}
                <span className="text-[8px] font-bold tracking-tighter uppercase text-outline ml-2">
                  {new Date(msg.timestamp).toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              </div>
            </div>
          ) : (
            <div
              className={`flex flex-col max-w-[75%] ${
                isOwnMessage ? "self-end items-end" : "self-start items-start"
              }`}
            >
              {!isOwnMessage && activeChat?.isPublic && (
                <span className="text-[9px] font-semibold text-outline mb-1 ml-1 uppercase tracking-wide">
                  {msg.senderFullName || msg.senderUsername}
                </span>
              )}
              <div className="relative group flex items-center gap-2">
                {isOwnMessage && !msg.isDeleted && (
                  <div className="relative shrink-0">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMenuMessageId(
                          activeMenuMessageId === msg.id ? null : msg.id
                        );
                      }}
                      className="opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity p-1 text-outline hover:text-on-surface rounded-full hover:bg-surface-container-high flex items-center justify-center shrink-0"
                    >
                      <span className="material-symbols-outlined text-sm">
                        more_vert
                      </span>
                    </button>

                    {activeMenuMessageId === msg.id && (
                      <div className="absolute right-0 top-[100%] mt-1 z-30 bg-surface-container-lowest border border-outline-variant/20 rounded-lg shadow-lg py-1 min-w-[100px] animate-[fadeIn_0.15s_ease-out]">
                        <button
                          type="button"
                          onClick={() =>
                            handleDeleteMessage(msg.id, msg.timestamp)
                          }
                          className="w-full text-left px-3 py-1.5 text-xs text-error hover:bg-surface-container-low transition-colors flex items-center gap-1.5 font-bold uppercase tracking-wider"
                        >
                          <span className="material-symbols-outlined text-sm text-error">
                            delete
                          </span>
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                )}

                <div
                  className={`p-3.5 rounded-2xl text-xs leading-relaxed ${
                    msg.isDeleted
                      ? `bg-surface-container-low/50 text-outline italic border border-outline-variant/10 ${isOwnMessage ? "rounded-br-sm" : "rounded-bl-sm"}`
                      : isOwnMessage
                        ? "bg-primary text-white rounded-br-sm shadow-sm shadow-primary/10"
                        : "bg-surface-container-lowest text-on-surface rounded-bl-sm border border-outline-variant/10"
                  }`}
                >
                  <p>{msg.content}</p>
                  <span
                    className={`text-[8px] font-bold mt-1.5 block tracking-tighter uppercase ${
                      isOwnMessage && !msg.isDeleted
                        ? "text-white/60 text-right"
                        : "text-outline"
                    }`}
                  >
                    {new Date(msg.timestamp).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      );
    },
    [userId, activeChat, currentMessages, activeMenuMessageId]
  );

  const getPartnerStatus = () => {
    if (!activeChat || activeChat.isPublic) return null;
    return onlineUsers[activeChat.id]?.status || "OFFLINE";
  };

  const isPending = activeChat?.status === "PENDING_REQUEST";
  const isPendingOrNeglected =
    activeChat?.status === "PENDING_REQUEST" ||
    activeChat?.status === "NEGLECTED";

  // Empty state
  if (!activeChat) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-center p-12 bg-surface-container-lowest/30">
        <div className="w-20 h-20 rounded-2xl bg-primary/5 flex items-center justify-center mb-6 border border-primary/10">
          <span className="material-symbols-outlined text-primary/30 text-4xl">
            chat_bubble
          </span>
        </div>
        <h2 className="text-xl font-headline font-black text-on-surface mb-2">
          Registry Focus Workspace
        </h2>
        <p className="text-xs text-outline max-w-[300px] leading-relaxed">
          Select a public system channel or a private curator connection from
          the sidebar to load messaging history.
        </p>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col h-full bg-surface-container-lowest/30 min-w-0">
      {/* Chat Header */}
      <div className="px-4 py-4 md:px-6 md:py-3.5 border-b border-outline-variant/10 flex items-center justify-between bg-surface-container-lowest z-10 shrink-0">
        <div className="flex items-center gap-2 md:gap-3">
          {onBackToList && (
            <button
              type="button"
              onClick={onBackToList}
              className="md:hidden p-1.5 -ml-1 text-outline hover:text-on-surface rounded-full hover:bg-surface-container-high transition-colors flex items-center justify-center"
            >
              <span className="material-symbols-outlined text-lg">
                arrow_back
              </span>
            </button>
          )}
          <div
            onClick={
              !activeChat.isPublic && onViewUserProfile
                ? () =>
                    onViewUserProfile({
                      id: activeChat.id,
                      username: activeChat.username,
                      status: activeChat.status,
                    })
                : undefined
            }
            className={`flex items-center gap-2 md:gap-3 ${
              !activeChat.isPublic
                ? "cursor-pointer hover:opacity-80 transition-opacity"
                : ""
            }`}
          >
            <div className="relative">
              <div
                className={`w-9 h-9 rounded-lg flex items-center justify-center font-bold text-xs ${
                  activeChat.isPublic
                    ? "bg-primary/10 text-primary"
                    : "bg-primary/5 text-primary border border-primary/10"
                }`}
              >
                {activeChat.isPublic ? (
                  <span className="material-symbols-outlined text-base">
                    language
                  </span>
                ) : (
                  (activeChat.fullName || activeChat.username)
                    .charAt(0)
                    .toUpperCase()
                )}
              </div>
              {!activeChat.isPublic && (
                <div
                  className={`absolute bottom-0 right-0 w-2.5 h-2.5 border-2 border-surface-container-lowest rounded-full ${
                    getPartnerStatus() === "ONLINE"
                      ? "bg-tertiary"
                      : "bg-outline/30"
                  }`}
                />
              )}
            </div>
            <div>
              <h3 className="font-bold text-sm text-on-surface leading-tight">
                {activeChat.fullName || activeChat.username}
              </h3>
              <p className="text-[9px] text-outline font-bold uppercase tracking-widest">
                {activeChat.isPublic ? (
                  <span className="text-primary">Curators Room</span>
                ) : (
                  <span>Status: {getPartnerStatus()}</span>
                )}
              </p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`px-2 py-0.5 rounded text-[8px] font-bold tracking-widest uppercase flex items-center gap-1 ${
              connected
                ? "bg-primary/10 text-primary"
                : "bg-error-container text-on-error-container"
            }`}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                connected ? "bg-primary animate-pulse" : "bg-error"
              }`}
            />
            {connected ? "Connected" : "Offline"}
          </span>

          {!activeChat.isPublic &&
            (activeChat.status === "ACCEPTED" ||
              activeChat.status === "CONTACT") && (
              <>
                <button
                  type="button"
                  onClick={() =>
                    initiateCall(
                      activeChat.id,
                      "AUDIO",
                      activeChat.fullName || activeChat.username
                    )
                  }
                  className="w-8 h-8 rounded-lg flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-high transition-colors"
                  title="Audio Call"
                >
                  <span className="material-symbols-outlined text-lg">
                    call
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() =>
                    initiateCall(
                      activeChat.id,
                      "VIDEO",
                      activeChat.fullName || activeChat.username
                    )
                  }
                  className="w-8 h-8 rounded-lg flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-high transition-colors"
                  title="Video Call"
                >
                  <span className="material-symbols-outlined text-lg">
                    videocam
                  </span>
                </button>
              </>
            )}

          {!activeChat.isPublic && (
            <div className="relative">
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowHeaderMenu(!showHeaderMenu);
                }}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-high transition-colors"
                title="Conversation Settings"
              >
                <span className="material-symbols-outlined text-lg">
                  more_vert
                </span>
              </button>
              {showHeaderMenu && (
                <div className="absolute right-0 mt-1.5 z-30 bg-surface-container-lowest border border-outline-variant/20 rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.08)] py-1.5 min-w-[140px] animate-[fadeIn_0.15s_ease-out]">
                  <button
                    type="button"
                    onClick={() => {
                      setShowHeaderMenu(false);
                      handleDeleteChat(activeChat.id);
                    }}
                    className="w-full text-left px-4 py-2.5 text-xs text-error hover:bg-surface-container-low transition-colors flex items-center gap-2 font-bold uppercase tracking-wider"
                  >
                    <span className="material-symbols-outlined text-base text-error">
                      delete
                    </span>
                    Delete Chat
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Inline Action Banner for Pending Requests */}
      {isPending && (
        <div className="px-6 py-3 bg-secondary/5 border-b border-secondary/10 flex items-center justify-between shrink-0 animate-[fadeIn_0.3s_ease-out]">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-lg">
              person_alert
            </span>
            <span className="text-xs font-bold text-on-surface">
              <span className="text-secondary">{activeChat.username}</span>{" "}
              wants to connect
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={bannerLoading !== null}
              onClick={() => handleBannerAction("accept")}
              className="px-3 py-1.5 bg-primary text-white text-[9px] font-bold uppercase tracking-widest rounded-lg hover:bg-primary-container transition-all disabled:opacity-50 scale-98-active"
            >
              {bannerLoading === "accept" ? "..." : "Accept"}
            </button>
            <button
              type="button"
              disabled={bannerLoading !== null}
              onClick={() => handleBannerAction("neglect")}
              className="px-3 py-1.5 border border-outline-variant/40 text-on-surface text-[9px] font-bold uppercase tracking-widest rounded-lg hover:bg-surface-container transition-all disabled:opacity-50 scale-98-active"
            >
              {bannerLoading === "neglect" ? "..." : "Ignore"}
            </button>
            <button
              type="button"
              disabled={bannerLoading !== null}
              onClick={() => handleBannerAction("block")}
              className="px-3 py-1.5 text-error text-[9px] font-bold uppercase tracking-widest rounded-lg hover:bg-error-container/20 transition-all disabled:opacity-50 scale-98-active"
            >
              {bannerLoading === "block" ? "..." : "Block"}
            </button>
          </div>
        </div>
      )}

      {/* Messages Viewport */}
      {currentMessages.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center text-center p-10">
          <span className="material-symbols-outlined text-outline/20 text-3xl mb-2">
            forum
          </span>
          <p className="text-xs text-outline font-medium">
            No previous records found. Write a prompt to begin.
          </p>
        </div>
      ) : (
        <Virtuoso
          className="flex-1 custom-scrollbar"
          style={{ flex: 1 }}
          data={currentMessages}
          firstItemIndex={10000 - currentMessages.length}
          initialTopMostItemIndex={9999}
          startReached={handleStartReached}
          followOutput={(isAtBottom) => (isAtBottom ? "smooth" : false)}
          itemContent={renderItem}
          components={{
            Header: () => {
              const hasMore = activeChat.isPublic
                ? hasMorePublicHistory
                : hasMorePrivateHistory[activeChat.id] !== false;
              if (!hasMore) return null;
              return (
                <div className="text-center py-2 shrink-0">
                  <span className="text-[9px] uppercase tracking-widest text-outline bg-surface-container-low px-3 py-1 rounded-full">
                    Scroll up to load historical ledger
                  </span>
                </div>
              );
            },
            List: VirtuosoList,
          }}
        />
      )}
      {activeChat && !activeChat.isPublic && typingUsers[activeChat.id] && (
        <div className="flex items-center gap-2 self-start mb-2 text-outline animate-[pulse_1.5s_ease-in-out_infinite] px-6 py-2 shrink-0">
          <span className="material-symbols-outlined text-sm">more_horiz</span>
          <span className="text-[10px] font-bold uppercase tracking-wider">
            {activeChat.username} is typing...
          </span>
        </div>
      )}

      {/* Message Input */}
      <div className="relative" ref={emojiPickerRef}>
        {showEmojiPicker && (
          <div className="absolute bottom-[100%] right-4 mb-2 z-50 shadow-2xl rounded-xl overflow-hidden border border-outline-variant/20">
            <EmojiPicker
              onEmojiClick={(emojiData) => {
                setInputText((prev) => prev + emojiData.emoji);
              }}
              theme={Theme.AUTO}
              emojiStyle={EmojiStyle.NATIVE}
            />
          </div>
        )}
        <form
          onSubmit={handleSendMessage}
          className="p-4 border-t border-outline-variant/10 bg-surface-container-lowest shrink-0"
        >
          {isPendingOrNeglected && (
            <div className="mb-3 px-3 py-2 bg-secondary/5 border border-secondary/10 rounded-lg flex items-center gap-2 text-[10px] text-on-surface font-semibold select-none animate-[fadeIn_0.2s_ease-out]">
              <span className="material-symbols-outlined text-secondary text-xs">
                warning
              </span>
              <span>
                Replying will automatically accept this request and save{" "}
                <span className="text-secondary font-bold">
                  {activeChat.username}
                </span>{" "}
                to your contacts.
              </span>
            </div>
          )}
          <div className="bg-surface-container-low rounded-xl p-1.5 flex items-center gap-2 border border-transparent focus-within:border-primary/30 transition-all">
            <input
              value={inputText}
              onChange={(e) => {
                setInputText(e.target.value);
                if (activeChat && !activeChat.isPublic) {
                  sendTypingIndicator(activeChat.id, true);
                }
              }}
              className="flex-1 bg-transparent border-none outline-none focus:ring-0 text-xs px-3 py-2 text-on-surface placeholder:text-outline"
              placeholder={`Compose message for ${activeChat.username}...`}
              type="text"
            />
            <button
              type="button"
              onClick={() => setShowEmojiPicker((prev) => !prev)}
              className="w-8 h-8 text-outline hover:text-on-surface rounded-lg flex items-center justify-center hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-sm">mood</span>
            </button>
            <button
              type="submit"
              disabled={!inputText.trim()}
              className="w-8 h-8 bg-primary text-white rounded-lg flex items-center justify-center hover:bg-primary-container transition-all shadow-sm scale-98-active disabled:opacity-40"
            >
              <span className="material-symbols-outlined text-sm">send</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
