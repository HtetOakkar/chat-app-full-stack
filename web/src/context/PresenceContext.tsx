"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
} from "react";
import { useAuth } from "./AuthContext";
import { useConnection } from "./ConnectionContext";
import { apiFetch } from "@/lib/api";

type OnlineUserStatus = {
  userId: number;
  status: string;
  timestamp: string;
  username: string;
};

type TypingIndicatorDto = {
  senderId: number;
  recipientId: number;
  isTyping: boolean;
};

interface PresenceContextType {
  onlineUsers: Record<
    number,
    { status: string; username: string; timestamp: string }
  >;
  typingUsers: Record<number, boolean>;
  sendTypingIndicator: (recipientId: number, isTyping: boolean) => void;
  sendStatusUpdate: (status: string) => void;
}

const PresenceContext = createContext<PresenceContextType | null>(null);

export const PresenceProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const { isAuthenticated } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();

  const [onlineUsers, setOnlineUsers] = useState<
    Record<number, { status: string; username: string; timestamp: string }>
  >({});
  const [typingUsers, setTypingUsers] = useState<Record<number, boolean>>({});

  const typingTimeoutsRef = useRef<Record<number, NodeJS.Timeout>>({});
  const lastSentTypingRef = useRef<Record<number, number>>({});

  // Clean up all timeouts on unmount
  useEffect(() => {
    const timeouts = typingTimeoutsRef.current;
    return () => {
      Object.values(timeouts).forEach((timeout) => {
        clearTimeout(timeout);
      });
    };
  }, []);

  // Subscribe to online and typing queues when connection is active
  useEffect(() => {
    if (!isAuthenticated) {
      // Defer state updates to avoid cascading render warning in useEffect
      const handle = setTimeout(() => {
        setOnlineUsers({});
        setTypingUsers({});
        lastSentTypingRef.current = {};
      }, 0);
      return () => clearTimeout(handle);
    }

    if (!connected) return;

    // 1. Subscribe to Online Notification Topic
    const unsubscribeOnline = subscribe("/user/queue/online", (message) => {
      const statusUpdate: OnlineUserStatus = JSON.parse(message.body);
      setOnlineUsers((prev) => ({
        ...prev,
        [statusUpdate.userId]: {
          status: statusUpdate.status,
          username: statusUpdate.username,
          timestamp: statusUpdate.timestamp,
        },
      }));
    });

    // 2. Subscribe to Typing Indicators
    const unsubscribeTyping = subscribe("/user/queue/typing", (message) => {
      const indicator: TypingIndicatorDto = JSON.parse(message.body);
      const { senderId, isTyping } = indicator;

      setTypingUsers((prev) => ({
        ...prev,
        [senderId]: isTyping,
      }));

      // Clear existing timeout if any
      if (typingTimeoutsRef.current[senderId]) {
        clearTimeout(typingTimeoutsRef.current[senderId]);
      }

      // Auto-clear typing status after 3 seconds if isTyping is true
      if (isTyping) {
        typingTimeoutsRef.current[senderId] = setTimeout(() => {
          setTypingUsers((prev) => ({
            ...prev,
            [senderId]: false,
          }));
        }, 3000);
      }
    });

    // Automatically publish ONLINE status
    if (stompClientRef.current && stompClientRef.current.connected) {
      stompClientRef.current.publish({
        destination: "/app/noti.status",
        body: JSON.stringify({ status: "ONLINE" }),
      });
    }

    // Fetch initially online users from REST API
    apiFetch("/api/v1/users/online")
      .then((onlineList: unknown) => {
        if (Array.isArray(onlineList)) {
          setOnlineUsers((prev) => {
            const newOnline = { ...prev };
            (
              onlineList as {
                userId: string | number;
                status: string;
                username: string;
              }[]
            ).forEach((user) => {
              newOnline[Number(user.userId)] = {
                status: user.status,
                username: user.username,
                timestamp: new Date().toISOString(),
              };
            });
            return newOnline;
          });
        }
      })
      .catch((err) => {
        if (err?.message !== "Unauthorized/Session Expired") {
          console.error("Failed to fetch online users:", err);
        }
      });

    return () => {
      unsubscribeOnline();
      unsubscribeTyping();
    };
  }, [connected, subscribe, isAuthenticated, stompClientRef]);

  // Send Typing Indicator with 2-second rate-limiting (debounce/throttle)
  const sendTypingIndicator = useCallback(
    (recipientId: number, isTyping: boolean) => {
      if (stompClientRef.current && connected) {
        const now = Date.now();
        const lastSent = lastSentTypingRef.current[recipientId] || 0;

        if (isTyping) {
          // Throttle: only send if at least 2 seconds (2000ms) have passed
          if (now - lastSent >= 2000) {
            stompClientRef.current.publish({
              destination: "/app/typing",
              body: JSON.stringify({ recipientId, isTyping }),
            });
            lastSentTypingRef.current[recipientId] = now;
          }
        } else {
          // Send "false" immediately
          stompClientRef.current.publish({
            destination: "/app/typing",
            body: JSON.stringify({ recipientId, isTyping }),
          });
          lastSentTypingRef.current[recipientId] = 0; // reset
        }
      }
    },
    [connected, stompClientRef]
  );

  const sendStatusUpdate = useCallback(
    (status: string) => {
      if (stompClientRef.current && connected) {
        stompClientRef.current.publish({
          destination: "/app/noti.status",
          body: JSON.stringify({ status }),
        });
      }
    },
    [connected, stompClientRef]
  );

  return (
    <PresenceContext.Provider
      value={{
        onlineUsers,
        typingUsers,
        sendTypingIndicator,
        sendStatusUpdate,
      }}
    >
      {children}
    </PresenceContext.Provider>
  );
};

export const usePresence = () => {
  const context = useContext(PresenceContext);
  if (!context) {
    throw new Error("usePresence must be used within a PresenceProvider");
  }
  return context;
};
