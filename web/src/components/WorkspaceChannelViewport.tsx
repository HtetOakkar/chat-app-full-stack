"use client";

import React, { useEffect, useState, useRef, useCallback } from "react";
import { useWorkspace, type ChannelDto } from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { apiFetch } from "@/lib/api";
import { Virtuoso } from "react-virtuoso";
import EmojiPicker, { Theme, EmojiStyle } from "emoji-picker-react";
import ChannelDetailsModal from "./ChannelDetailsModal";

interface ChannelMessage {
  id: number;
  content: string;
  senderMemberId?: number;
  senderUserId?: number;
  senderId?: number;
  senderUsername: string;
  senderFullName?: string;
  timestamp?: string;
  sentAt?: string;
  isDeleted?: boolean;
  messageType?: string;
}

const formatChannelTime = (dateStr?: string): string => {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getChannelTime = (msg: ChannelMessage): number => {
  const ts = msg.timestamp || msg.sentAt;
  if (!ts) return 0;
  const time = new Date(ts).getTime();
  return isNaN(time) ? 0 : time;
};

const VirtuosoChannelList = React.forwardRef<HTMLDivElement, any>(
  ({ children, style, ...props }, ref) => (
    <div
      {...props}
      ref={ref}
      style={{ ...style }}
      className="p-6 flex flex-col gap-3 min-w-0 max-w-full overflow-x-hidden"
    >
      {children}
    </div>
  )
);
VirtuosoChannelList.displayName = "VirtuosoChannelList";

export default function WorkspaceChannelViewport({
  channel,
  onBack,
}: {
  channel: ChannelDto;
  onBack?: () => void;
}) {
  const { currentWorkspace } = useWorkspace();
  const { userId } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();

  const [messages, setMessages] = useState<ChannelMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [modalInitialTab, setModalInitialTab] = useState<
    "members" | "add" | "email"
  >("members");
  const emojiPickerRef = useRef<HTMLDivElement | null>(null);

  const cursorRef = useRef<string | null>(null);
  const hasMoreRef = useRef<boolean>(true);
  const loadingHistoryRef = useRef(false);

  const channelId = channel.id;

  // Fetch channel history
  const fetchMessages = useCallback(
    async (isInitial = false) => {
      if (!currentWorkspace || !channelId || loadingHistoryRef.current) return;
      if (!isInitial && !hasMoreRef.current) return;

      loadingHistoryRef.current = true;
      const currentCursor = isInitial ? null : cursorRef.current;

      try {
        const url = `/api/channels/${channelId}/messages?limit=30${
          currentCursor ? `&cursor=${encodeURIComponent(currentCursor)}` : ""
        }`;
        const data = await apiFetch(url);
        const fetched: ChannelMessage[] = data.messages || [];
        const nextCursor = data.nextCursor;

        if (!nextCursor || fetched.length === 0) {
          hasMoreRef.current = false;
        } else {
          cursorRef.current = nextCursor;
        }

        setMessages((prev) => {
          if (isInitial) {
            const sorted = [...fetched].sort(
              (a, b) => getChannelTime(a) - getChannelTime(b)
            );
            return sorted;
          }
          const existingIds = new Set(prev.map((m) => m.id));
          const newUnique = fetched.filter((m) => !existingIds.has(m.id));
          const combined = [...newUnique, ...prev];
          combined.sort((a, b) => getChannelTime(a) - getChannelTime(b));
          return combined;
        });
      } catch (err) {
        console.warn("Failed to fetch channel messages:", err);
      } finally {
        loadingHistoryRef.current = false;
      }
    },
    [currentWorkspace, channelId]
  );

  useEffect(() => {
    cursorRef.current = null;
    hasMoreRef.current = true;
    setMessages([]);
    fetchMessages(true);
  }, [channelId, fetchMessages]);

  const loadMoreHistory = useCallback(() => {
    fetchMessages(false);
  }, [fetchMessages]);

  // STOMP Subscription for Channel
  useEffect(() => {
    if (!connected || !currentWorkspace || !channelId) return;

    const wId = currentWorkspace.id;

    const unsub = subscribe(
      `/topic/workspace.${wId}.channel.${channelId}`,
      (frame) => {
        try {
          const msg: ChannelMessage = JSON.parse(frame.body);
          setMessages((prev) => {
            if (prev.some((m) => m.id === msg.id)) return prev;
            return [...prev, msg];
          });
        } catch {}
      }
    );

    return () => {
      unsub();
    };
  }, [connected, currentWorkspace, channelId, subscribe]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !inputText.trim() ||
      !stompClientRef.current ||
      !connected ||
      !currentWorkspace
    )
      return;

    const destination = `/app/workspace.${currentWorkspace.id}.channel.${channel.id}.send`;
    stompClientRef.current.publish({
      destination,
      body: JSON.stringify({ content: inputText.trim() }),
    });

    setInputText("");
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-surface-container-lowest/30 min-w-0 overflow-hidden">
      {/* Header */}
      <div className="px-3 md:px-6 py-3.5 border-b border-outline-variant/10 flex items-center justify-between bg-surface-container-lowest z-10 shrink-0 min-w-0">
        <div className="flex items-center gap-2 md:gap-2.5 min-w-0">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="md:hidden w-11 h-11 -ml-2 text-outline hover:text-on-surface hover:bg-surface-container-high rounded-xl transition-colors flex items-center justify-center shrink-0"
              aria-label="Back to channels"
            >
              <span className="material-symbols-outlined text-xl">
                arrow_back
              </span>
            </button>
          )}
          <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary shrink-0">
            <span className="material-symbols-outlined text-base">
              {channel.type === "PRIVATE" ? "lock" : "tag"}
            </span>
          </div>
          <div className="min-w-0">
            <h3 className="font-extrabold text-sm text-on-surface leading-tight truncate">
              #{channel.name}
            </h3>
            {channel.description && (
              <p className="text-[11px] text-outline truncate max-w-xs md:max-w-md hidden sm:block">
                {channel.description}
              </p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {channel.type === "PRIVATE" && (
            <button
              type="button"
              onClick={() => {
                setModalInitialTab("add");
                setShowDetailsModal(true);
              }}
              className="px-2.5 py-1 rounded-xl bg-primary/10 hover:bg-primary/20 text-primary text-xs font-bold transition-colors flex items-center gap-1"
              title="Add Members"
            >
              <span className="material-symbols-outlined text-sm font-bold">
                person_add
              </span>
              <span className="hidden sm:inline">Add Members</span>
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              setModalInitialTab("members");
              setShowDetailsModal(true);
            }}
            className="px-2.5 py-1 rounded-full bg-surface-container hover:bg-surface-container-high text-outline hover:text-on-surface text-[10px] font-bold transition-colors flex items-center gap-1 cursor-pointer"
            title="View Channel Details & Members"
          >
            <span className="material-symbols-outlined text-xs">group</span>
            <span>
              {channel.memberCount}{" "}
              {channel.memberCount === 1 ? "member" : "members"}
            </span>
          </button>
        </div>
      </div>

      {/* Messages Viewport */}
      {messages.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center text-center p-10">
          <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center text-primary mb-3">
            <span className="material-symbols-outlined text-2xl">
              {channel.type === "PRIVATE" ? "lock" : "tag"}
            </span>
          </div>
          <h4 className="font-extrabold text-sm text-on-surface mb-1">
            Welcome to #{channel.name}!
          </h4>
          <p className="text-xs text-outline max-w-sm">
            This is the start of the #{channel.name} channel. Send a message to
            start the conversation!
          </p>
        </div>
      ) : (
        <Virtuoso
          className="flex-1 custom-scrollbar overflow-x-hidden"
          style={{ flex: 1 }}
          data={messages}
          startReached={loadMoreHistory}
          followOutput={(isAtBottom) => (isAtBottom ? "smooth" : false)}
          components={{
            List: VirtuosoChannelList,
          }}
          itemContent={(index, msg) => {
            const timeStr = formatChannelTime(msg.timestamp || msg.sentAt);

            // System activity message rendering (e.g. @alice added @bob to the channel)
            if (
              msg.messageType === "SYSTEM" ||
              msg.messageType === "JOIN" ||
              msg.messageType === "LEAVE"
            ) {
              return (
                <div
                  key={msg.id}
                  className="flex items-center justify-center my-2.5 w-full select-none"
                >
                  <div className="px-3.5 py-1.5 rounded-full bg-surface-container/70 border border-outline-variant/10 text-outline text-[11px] font-medium flex items-center gap-1.5 shadow-2xs">
                    <span className="material-symbols-outlined text-xs text-primary/80">
                      {msg.messageType === "LEAVE" ? "logout" : "info"}
                    </span>
                    <span className="text-on-surface/80">{msg.content}</span>
                    {timeStr && (
                      <span className="text-[9px] text-outline/60 ml-1 font-mono">
                        {timeStr}
                      </span>
                    )}
                  </div>
                </div>
              );
            }

            const senderId = msg.senderId ?? msg.senderUserId;
            const isOwn = senderId === userId;

            return (
              <div
                key={msg.id}
                className={`flex flex-col mb-3 min-w-0 w-full ${isOwn ? "items-end" : "items-start"}`}
              >
                {!isOwn && (
                  <span className="text-[10px] font-bold text-outline mb-1 ml-1 truncate max-w-full">
                    {msg.senderFullName || msg.senderUsername}
                  </span>
                )}
                <div
                  className={`p-3.5 rounded-2xl text-xs max-w-[75%] leading-relaxed min-w-0 break-words [overflow-wrap:anywhere] ${
                    isOwn
                      ? "bg-primary text-white rounded-br-sm shadow-sm"
                      : "bg-surface-container-lowest text-on-surface rounded-bl-sm border border-outline-variant/10 shadow-xs"
                  }`}
                >
                  <p className="break-words [overflow-wrap:anywhere] whitespace-pre-wrap">
                    {msg.content}
                  </p>
                  {timeStr && (
                    <span
                      className={`text-[8px] font-bold mt-1.5 block tracking-tighter uppercase ${
                        isOwn ? "text-white/60 text-right" : "text-outline"
                      }`}
                    >
                      {timeStr}
                    </span>
                  )}
                </div>
              </div>
            );
          }}
        />
      )}

      {/* Input */}
      <div className="relative shrink-0" ref={emojiPickerRef}>
        {showEmojiPicker && (
          <>
            {/* Mobile Backdrop */}
            <div
              className="fixed inset-0 bg-black/40 z-50 sm:hidden"
              onClick={() => setShowEmojiPicker(false)}
            />
            <div className="fixed bottom-0 left-0 right-0 sm:absolute sm:bottom-[100%] sm:right-4 sm:left-auto mb-0 sm:mb-2 z-50 shadow-2xl rounded-t-3xl sm:rounded-2xl overflow-hidden border border-outline-variant/20 max-h-[60vh] sm:max-h-none flex justify-center bg-surface-container-lowest">
              <EmojiPicker
                onEmojiClick={(emojiData) => {
                  setInputText((prev) => prev + emojiData.emoji);
                  if (
                    typeof window !== "undefined" &&
                    window.innerWidth < 640
                  ) {
                    setShowEmojiPicker(false);
                  }
                }}
                theme={Theme.AUTO}
                emojiStyle={EmojiStyle.NATIVE}
              />
            </div>
          </>
        )}
        <form
          onSubmit={handleSendMessage}
          className="p-3 md:p-4 border-t border-outline-variant/10 bg-surface-container-lowest shrink-0"
        >
          <div className="bg-surface-container-low rounded-2xl p-1.5 flex items-center gap-2 border border-transparent focus-within:border-primary/30 transition-all min-w-0">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={`Message #${channel.name}...`}
              className="flex-1 min-w-0 bg-transparent border-none outline-none focus:ring-0 text-xs px-3 py-2 text-on-surface placeholder:text-outline"
            />
            <button
              type="button"
              onClick={() => setShowEmojiPicker(!showEmojiPicker)}
              aria-label="Insert Emoji"
              className="w-10 h-10 md:w-8 md:h-8 text-outline hover:text-on-surface rounded-xl flex items-center justify-center hover:bg-surface-container-high transition-colors shrink-0"
            >
              <span className="material-symbols-outlined text-base">mood</span>
            </button>
            <button
              type="submit"
              disabled={!inputText.trim()}
              aria-label="Send Message"
              className="w-10 h-10 md:w-8 md:h-8 bg-primary text-white rounded-xl flex items-center justify-center hover:bg-primary/90 transition-all shadow-xs disabled:opacity-40 shrink-0"
            >
              <span className="material-symbols-outlined text-sm">send</span>
            </button>
          </div>
        </form>
      </div>

      {/* Channel Details & Members Modal */}
      <ChannelDetailsModal
        isOpen={showDetailsModal}
        onClose={() => setShowDetailsModal(false)}
        channel={channel}
        initialTab={modalInitialTab}
      />
    </div>
  );
}
