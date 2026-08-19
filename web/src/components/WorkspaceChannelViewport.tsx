"use client";

import React, { useEffect, useState, useRef, useCallback } from "react";
import { useWorkspace, type ChannelDto } from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { apiFetch } from "@/lib/api";
import { Virtuoso } from "react-virtuoso";
import EmojiPicker, { Theme, EmojiStyle } from "emoji-picker-react";

interface ChannelMessage {
  id: number;
  content: string;
  senderMemberId: number;
  senderUserId: number;
  senderUsername: string;
  senderFullName?: string;
  sentAt: string;
  isDeleted?: boolean;
}

export default function WorkspaceChannelViewport({
  channel,
}: {
  channel: ChannelDto;
}) {
  const { currentWorkspace, typingMap } = useWorkspace();
  const { userId, username } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();

  const [messages, setMessages] = useState<ChannelMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const emojiPickerRef = useRef<HTMLDivElement | null>(null);
  const loadingHistoryRef = useRef(false);

  // Load initial channel messages
  const loadChannelHistory = useCallback(async () => {
    if (!currentWorkspace || !channel || loadingHistoryRef.current || !hasMore)
      return;
    loadingHistoryRef.current = true;

    try {
      const url = `/api/channels/${channel.id}/messages?limit=30${
        cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""
      }`;
      const data = await apiFetch(url);
      const fetched: ChannelMessage[] = data.messages || [];
      const nextCursor = data.nextCursor;

      if (!nextCursor || fetched.length === 0) {
        setHasMore(false);
      } else {
        setCursor(nextCursor);
      }

      setMessages((prev) => {
        const existingIds = new Set(prev.map((m) => m.id));
        const newUnique = fetched.filter((m) => !existingIds.has(m.id));
        const combined = [...newUnique, ...prev];
        combined.sort(
          (a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime()
        );
        return combined;
      });
    } catch (err) {
      console.warn("Failed to fetch channel messages:", err);
    } finally {
      loadingHistoryRef.current = false;
    }
  }, [currentWorkspace, channel, cursor, hasMore]);

  useEffect(() => {
    setMessages([]);
    setCursor(null);
    setHasMore(true);
    loadChannelHistory();
  }, [channel.id]);

  // STOMP Subscription for Channel
  useEffect(() => {
    if (!connected || !currentWorkspace || !channel) return;

    const wId = currentWorkspace.id;
    const cId = channel.id;

    const unsub = subscribe(
      `/topic/workspace.${wId}.channel.${cId}`,
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
  }, [connected, currentWorkspace, channel, subscribe]);

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
    <div className="flex-1 flex flex-col h-full bg-surface-container-lowest/30 min-w-0">
      {/* Header */}
      <div className="px-6 py-3.5 border-b border-outline-variant/10 flex items-center justify-between bg-surface-container-lowest z-10 shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
            <span className="material-symbols-outlined text-base">
              {channel.type === "PRIVATE" ? "lock" : "tag"}
            </span>
          </div>
          <div>
            <h3 className="font-extrabold text-sm text-on-surface leading-tight">
              #{channel.name}
            </h3>
            {channel.description && (
              <p className="text-[11px] text-outline truncate max-w-md">
                {channel.description}
              </p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="px-2.5 py-1 rounded-full bg-surface-container text-outline text-[10px] font-bold">
            {channel.memberCount}{" "}
            {channel.memberCount === 1 ? "member" : "members"}
          </span>
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
          className="flex-1 custom-scrollbar p-6"
          data={messages}
          startReached={loadChannelHistory}
          followOutput={(isAtBottom) => (isAtBottom ? "smooth" : false)}
          itemContent={(index, msg) => {
            const isOwn = msg.senderUserId === userId;
            return (
              <div
                key={msg.id}
                className={`flex flex-col mb-3 ${isOwn ? "items-end" : "items-start"}`}
              >
                {!isOwn && (
                  <span className="text-[10px] font-bold text-outline mb-1 ml-1">
                    {msg.senderFullName || msg.senderUsername}
                  </span>
                )}
                <div
                  className={`p-3.5 rounded-2xl text-xs max-w-[75%] leading-relaxed ${
                    isOwn
                      ? "bg-primary text-white rounded-br-sm shadow-sm"
                      : "bg-surface-container-lowest text-on-surface rounded-bl-sm border border-outline-variant/10 shadow-xs"
                  }`}
                >
                  <p>{msg.content}</p>
                  <span
                    className={`text-[8px] font-bold mt-1.5 block tracking-tighter uppercase ${
                      isOwn ? "text-white/60 text-right" : "text-outline"
                    }`}
                  >
                    {new Date(msg.sentAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </span>
                </div>
              </div>
            );
          }}
        />
      )}

      {/* Input */}
      <div className="relative" ref={emojiPickerRef}>
        {showEmojiPicker && (
          <div className="absolute bottom-[100%] right-4 mb-2 z-50 shadow-2xl rounded-2xl overflow-hidden border border-outline-variant/20">
            <EmojiPicker
              onEmojiClick={(emojiData) =>
                setInputText((prev) => prev + emojiData.emoji)
              }
              theme={Theme.AUTO}
              emojiStyle={EmojiStyle.NATIVE}
            />
          </div>
        )}
        <form
          onSubmit={handleSendMessage}
          className="p-4 border-t border-outline-variant/10 bg-surface-container-lowest shrink-0"
        >
          <div className="bg-surface-container-low rounded-2xl p-1.5 flex items-center gap-2 border border-transparent focus-within:border-primary/30 transition-all">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={`Message #${channel.name}...`}
              className="flex-1 bg-transparent border-none outline-none focus:ring-0 text-xs px-3 py-2 text-on-surface placeholder:text-outline"
            />
            <button
              type="button"
              onClick={() => setShowEmojiPicker(!showEmojiPicker)}
              className="w-8 h-8 text-outline hover:text-on-surface rounded-xl flex items-center justify-center hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-base">mood</span>
            </button>
            <button
              type="submit"
              disabled={!inputText.trim()}
              className="w-8 h-8 bg-primary text-white rounded-xl flex items-center justify-center hover:bg-primary/90 transition-all shadow-xs disabled:opacity-40"
            >
              <span className="material-symbols-outlined text-sm">send</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
