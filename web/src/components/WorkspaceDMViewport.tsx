"use client";

import React, { useEffect, useState, useRef, useCallback } from "react";
import {
  useWorkspace,
  type DirectMessageDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";
import { useConnection } from "@/context/ConnectionContext";
import { useCall } from "@/context/CallContext";
import { apiFetch } from "@/lib/api";
import { Virtuoso } from "react-virtuoso";
import EmojiPicker, { Theme, EmojiStyle } from "emoji-picker-react";

interface DMMessage {
  id: number;
  content: string;
  senderMemberId?: number;
  senderUserId?: number;
  senderId?: number;
  recipientId?: number;
  senderUsername: string;
  senderFullName?: string;
  timestamp?: string;
  sentAt?: string;
  messageType?: string;
  callOutcome?: string;
  callDuration?: number;
  isDeleted?: boolean;
}

const formatDMTime = (dateStr?: string): string => {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getDMTime = (msg: DMMessage): number => {
  const ts = msg.timestamp || msg.sentAt;
  if (!ts) return 0;
  const time = new Date(ts).getTime();
  return isNaN(time) ? 0 : time;
};

export default function WorkspaceDMViewport({ dm }: { dm: DirectMessageDto }) {
  const { currentWorkspace, presenceMap, typingMap } = useWorkspace();
  const { userId } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();
  const { initiateCall } = useCall();

  const target = dm.targetMember;
  const isOnline = presenceMap[target.userId] === "ONLINE";

  const [messages, setMessages] = useState<DMMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const emojiPickerRef = useRef<HTMLDivElement | null>(null);
  const loadingHistoryRef = useRef(false);

  // Load initial DM history
  const loadDMHistory = useCallback(async () => {
    if (!currentWorkspace || !target || loadingHistoryRef.current || !hasMore)
      return;
    loadingHistoryRef.current = true;

    try {
      const url = `/api/workspaces/${currentWorkspace.id}/dm/${target.userId}/messages?limit=30${
        cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""
      }`;
      const data = await apiFetch(url);
      const fetched: DMMessage[] = data.messages || [];
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
        combined.sort((a, b) => getDMTime(a) - getDMTime(b));
        return combined;
      });
    } catch (err) {
      console.warn("Failed to fetch DM messages:", err);
    } finally {
      loadingHistoryRef.current = false;
    }
  }, [currentWorkspace, target, cursor, hasMore]);

  useEffect(() => {
    setMessages([]);
    setCursor(null);
    setHasMore(true);
    loadDMHistory();
  }, [target.userId]);

  // Listen to window workspace:dm:received event
  useEffect(() => {
    const handleDMReceived = (e: Event) => {
      const custom = e as CustomEvent;
      const msg: DMMessage = custom.detail;
      if (!msg) return;

      const senderId = msg.senderId ?? msg.senderUserId;
      const recipientId = msg.recipientId;

      if (
        senderId === target.userId ||
        recipientId === target.userId ||
        senderId === userId
      ) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === msg.id)) return prev;
          return [...prev, msg];
        });
      }
    };

    window.addEventListener("workspace:dm:received", handleDMReceived);
    return () => {
      window.removeEventListener("workspace:dm:received", handleDMReceived);
    };
  }, [target.userId, userId]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !inputText.trim() ||
      !stompClientRef.current ||
      !connected ||
      !currentWorkspace
    )
      return;

    const destination = `/app/workspace.${currentWorkspace.id}.dm.send`;
    stompClientRef.current.publish({
      destination,
      body: JSON.stringify({
        recipientId: target.userId,
        content: inputText.trim(),
      }),
    });

    setInputText("");
  };

  const handleCall = (callType: "AUDIO" | "VIDEO") => {
    initiateCall(target.userId, callType, target.fullName || target.username);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-surface-container-lowest/30 min-w-0">
      {/* Header */}
      <div className="px-6 py-3.5 border-b border-outline-variant/10 flex items-center justify-between bg-surface-container-lowest z-10 shrink-0">
        <div className="flex items-center gap-3">
          <div className="relative">
            <div className="w-9 h-9 rounded-2xl bg-primary/10 flex items-center justify-center font-bold text-primary text-sm">
              {target.username.charAt(0).toUpperCase()}
            </div>
            <span
              className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full ring-2 ring-surface-container-lowest ${
                isOnline ? "bg-emerald-500" : "bg-neutral-400"
              }`}
            />
          </div>
          <div>
            <h3 className="font-extrabold text-sm text-on-surface leading-tight">
              {target.fullName || target.username}
            </h3>
            <p className="text-[10px] font-bold text-outline uppercase tracking-wider">
              {isOnline ? (
                <span className="text-emerald-500">Active Now</span>
              ) : (
                <span>Offline</span>
              )}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => handleCall("AUDIO")}
            title="Audio Call"
            className="w-8 h-8 rounded-xl bg-surface-container-low hover:bg-primary/10 text-outline hover:text-primary flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-lg">call</span>
          </button>
          <button
            type="button"
            onClick={() => handleCall("VIDEO")}
            title="Video Call"
            className="w-8 h-8 rounded-xl bg-surface-container-low hover:bg-primary/10 text-outline hover:text-primary flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-lg">videocam</span>
          </button>
        </div>
      </div>

      {/* Messages */}
      {messages.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center text-center p-10">
          <div className="w-16 h-16 rounded-3xl bg-primary/10 flex items-center justify-center text-primary mb-3">
            <span className="material-symbols-outlined text-2xl">chat</span>
          </div>
          <h4 className="font-extrabold text-sm text-on-surface mb-1">
            Chatting with {target.fullName || target.username}
          </h4>
          <p className="text-xs text-outline max-w-sm">
            This is the direct messaging history for your workspace. Send a note
            to say hi!
          </p>
        </div>
      ) : (
        <Virtuoso
          className="flex-1 custom-scrollbar p-6"
          data={messages}
          startReached={loadDMHistory}
          followOutput={(isAtBottom) => (isAtBottom ? "smooth" : false)}
          itemContent={(index, msg) => {
            const senderId = msg.senderId ?? msg.senderUserId;
            const isOwn = senderId === userId;
            const isCall =
              msg.messageType === "AUDIO" || msg.messageType === "VIDEO";
            const timeStr = formatDMTime(msg.timestamp || msg.sentAt);

            if (isCall) {
              return (
                <div key={msg.id} className="flex justify-center my-2">
                  <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-surface-container-low border border-outline-variant/15 text-xs text-on-surface">
                    <span className="material-symbols-outlined text-sm">
                      {msg.messageType === "VIDEO" ? "videocam" : "call"}
                    </span>
                    <span className="font-semibold">
                      {msg.callOutcome || "Call Ended"}
                    </span>
                    {timeStr && (
                      <span className="text-[10px] text-outline ml-1">
                        {timeStr}
                      </span>
                    )}
                  </div>
                </div>
              );
            }

            return (
              <div
                key={msg.id}
                className={`flex flex-col mb-3 ${isOwn ? "items-end" : "items-start"}`}
              >
                <div
                  className={`p-3.5 rounded-2xl text-xs max-w-[75%] leading-relaxed ${
                    isOwn
                      ? "bg-primary text-white rounded-br-sm shadow-sm"
                      : "bg-surface-container-lowest text-on-surface rounded-bl-sm border border-outline-variant/10 shadow-xs"
                  }`}
                >
                  <p>{msg.content}</p>
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
              placeholder={`Message ${target.fullName || target.username}...`}
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
