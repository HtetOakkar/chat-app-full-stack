"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
} from "react";
import { useAuth } from "./AuthContext";
import { useConnection } from "./ConnectionContext";
import { apiFetch } from "@/lib/api";

export type MessageDto = {
  id: number;
  content: string;
  senderId: number;
  senderUsername: string;
  senderFullName?: string | null;
  recipientId: number | null;
  timestamp: string;
  messageType: string;
  isDeleted?: boolean;
};

interface MessageStoreContextType {
  publicMessages: MessageDto[];
  privateMessages: Record<number, MessageDto[]>;
  sendPublicMessage: (content: string) => void;
  sendPrivateMessage: (recipientId: number, content: string) => void;
  loadPublicHistory: () => Promise<void>;
  loadPrivateHistory: (contactUserId: number) => Promise<void>;
  hasMorePublicHistory: boolean;
  hasMorePrivateHistory: Record<number, boolean>;
}

const MessageStoreContext = createContext<MessageStoreContextType | null>(null);

const MAX_MESSAGES = 500;

/**
 * Pure merge helper: deduplicates incoming messages against existing ones,
 * sorts chronologically, and caps at MAX_MESSAGES.
 * Returns the new array — no side effects.
 */
function mergeMessages(
  existing: MessageDto[],
  incoming: MessageDto[]
): MessageDto[] {
  const existingIds = new Set(existing.map((m) => m.id));
  const newUnique = incoming.filter((m) => !existingIds.has(m.id));

  if (newUnique.length === 0) return existing;

  let combined = [...newUnique, ...existing];
  combined.sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  );

  if (combined.length > MAX_MESSAGES) {
    combined = combined.slice(combined.length - MAX_MESSAGES);
  }

  return combined;
}

/**
 * Pure upsert helper for a single live message: appends if new, updates in-place if duplicate.
 */
function upsertMessage(existing: MessageDto[], msg: MessageDto): MessageDto[] {
  const idx = existing.findIndex((m) => m.id === msg.id);
  if (idx !== -1) {
    // Update existing message in-place
    const updated = [...existing];
    updated[idx] = msg;
    return updated;
  }

  let newMessages = [...existing, msg];

  if (newMessages.length > MAX_MESSAGES) {
    newMessages = newMessages.slice(newMessages.length - MAX_MESSAGES);
  }

  return newMessages;
}

export const MessageStoreProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const { token, isAuthenticated, userId } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();

  const [publicMessages, setPublicMessages] = useState<MessageDto[]>([]);
  const [privateMessages, setPrivateMessages] = useState<
    Record<number, MessageDto[]>
  >({});

  const [hasMorePublicHistory, setHasMorePublicHistory] = useState(true);
  const [hasMorePrivateHistory, setHasMorePrivateHistory] = useState<
    Record<number, boolean>
  >({});
  const [publicCursor, setPublicCursor] = useState<string | null>(null);
  const [privateCursors, setPrivateCursors] = useState<
    Record<number, string | null>
  >({});

  // Request deduplication refs
  const loadingPublicRef = useRef(false);
  const loadingPrivateRef = useRef<Record<number, boolean>>({});

  useEffect(() => {
    if (!isAuthenticated) {
      setPublicMessages([]);
      setPrivateMessages({});
      setHasMorePublicHistory(true);
      setHasMorePrivateHistory({});
      setPublicCursor(null);
      setPrivateCursors({});
      loadingPublicRef.current = false;
      loadingPrivateRef.current = {};
      return;
    }

    // Subscribe to Public Topic via ConnectionContext
    const unsubscribePublic = subscribe("/topic/public", (message) => {
      const msg: MessageDto = JSON.parse(message.body);
      setPublicMessages((prev) => upsertMessage(prev, msg));
    });

    // Subscribe to Private Messages Queue via ConnectionContext
    const unsubscribePrivate = subscribe("/user/queue/messages", (message) => {
      const msg: MessageDto = JSON.parse(message.body);
      const partnerId =
        msg.senderId === userId ? msg.recipientId : msg.senderId;
      if (!partnerId) return;

      setPrivateMessages((prev) => {
        const current = prev[partnerId] || [];
        return { ...prev, [partnerId]: upsertMessage(current, msg) };
      });

      if (typeof window !== "undefined") {
        window.dispatchEvent(
          new CustomEvent("message:received", { detail: msg })
        );
      }
    });

    // Subscribe to Private Message Requests Queue via ConnectionContext
    const unsubscribeRequests = subscribe("/user/queue/requests", (message) => {
      const msg: MessageDto = JSON.parse(message.body);
      const partnerId =
        msg.senderId === userId ? msg.recipientId : msg.senderId;
      if (!partnerId) return;

      setPrivateMessages((prev) => {
        const current = prev[partnerId] || [];
        return { ...prev, [partnerId]: upsertMessage(current, msg) };
      });

      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("contacts:updated"));
        window.dispatchEvent(
          new CustomEvent("message:received", { detail: msg })
        );
      }
    });

    return () => {
      unsubscribePublic();
      unsubscribePrivate();
      unsubscribeRequests();
    };
  }, [subscribe, isAuthenticated, userId]);

  // chat:deleted event listener
  useEffect(() => {
    const handleChatDeleted = (event: Event) => {
      const customEvent = event as CustomEvent;
      const { contactUserId } = customEvent.detail;
      setPrivateMessages((prev) => {
        const next = { ...prev };
        delete next[contactUserId];
        return next;
      });
      setPrivateCursors((prev) => {
        const next = { ...prev };
        delete next[contactUserId];
        return next;
      });
      setHasMorePrivateHistory((prev) => {
        const next = { ...prev };
        delete next[contactUserId];
        return next;
      });
    };

    window.addEventListener("chat:deleted", handleChatDeleted);
    return () => {
      window.removeEventListener("chat:deleted", handleChatDeleted);
    };
  }, []);

  const sendPublicMessage = (content: string) => {
    if (stompClientRef.current && connected) {
      stompClientRef.current.publish({
        destination: "/app/chat.public",
        body: JSON.stringify({ content }),
      });
    }
  };

  const sendPrivateMessage = (recipientId: number, content: string) => {
    if (stompClientRef.current && connected) {
      const tempMsg = {
        content,
        recipientId,
      };
      stompClientRef.current.publish({
        destination: "/app/chat.private",
        body: JSON.stringify(tempMsg),
      });
    }
  };

  const loadPublicHistory = async () => {
    if (!hasMorePublicHistory || loadingPublicRef.current) return;
    loadingPublicRef.current = true;

    try {
      const url = `/api/v1/messages/public?limit=30${publicCursor ? `&cursor=${encodeURIComponent(publicCursor)}` : ""}`;
      const data = await apiFetch(url);

      const messages: MessageDto[] = data.messages || [];
      const nextCursor = data.nextCursor;

      if (!nextCursor || messages.length === 0) {
        setHasMorePublicHistory(false);
      } else {
        setPublicCursor(nextCursor);
      }

      setPublicMessages((prev) => mergeMessages(prev, messages));
    } catch (err: any) {
      if (err?.message !== "Unauthorized/Session Expired") {
        console.error("Error loading public history:", err);
      }
    } finally {
      loadingPublicRef.current = false;
    }
  };

  const loadPrivateHistory = async (contactUserId: number) => {
    if (hasMorePrivateHistory[contactUserId] === false) return;
    if (loadingPrivateRef.current[contactUserId]) return;

    loadingPrivateRef.current[contactUserId] = true;
    const currentCursor = privateCursors[contactUserId];

    try {
      const url = `/api/v1/messages/private/${contactUserId}?limit=30${currentCursor ? `&cursor=${encodeURIComponent(currentCursor)}` : ""}`;
      const data = await apiFetch(url);

      const messages: MessageDto[] =
        data.messages || (Array.isArray(data) ? data : []);
      const nextCursor = data.nextCursor || null;

      if (!nextCursor || messages.length === 0) {
        setHasMorePrivateHistory((prev) => ({
          ...prev,
          [contactUserId]: false,
        }));
      } else {
        setPrivateCursors((prev) => ({ ...prev, [contactUserId]: nextCursor }));
      }

      setPrivateMessages((prev) => {
        const current = prev[contactUserId] || [];
        return {
          ...prev,
          [contactUserId]: mergeMessages(current, messages),
        };
      });
    } catch (err: any) {
      if (err?.message !== "Unauthorized/Session Expired") {
        console.error(
          `Error loading private history for ${contactUserId}:`,
          err
        );
      }
    } finally {
      loadingPrivateRef.current[contactUserId] = false;
    }
  };

  return (
    <MessageStoreContext.Provider
      value={{
        publicMessages,
        privateMessages,
        sendPublicMessage,
        sendPrivateMessage,
        loadPublicHistory,
        loadPrivateHistory,
        hasMorePublicHistory,
        hasMorePrivateHistory,
      }}
    >
      {children}
    </MessageStoreContext.Provider>
  );
};

export const useMessageStore = () => {
  const context = useContext(MessageStoreContext);
  if (!context) {
    throw new Error(
      "useMessageStore must be used within a MessageStoreProvider"
    );
  }
  return context;
};
