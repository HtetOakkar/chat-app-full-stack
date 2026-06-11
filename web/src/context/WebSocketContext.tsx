"use client";

import React, { createContext, useContext, useEffect, useState, useRef } from "react";
import { useAuth } from "./AuthContext";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiFetch } from "@/lib/api";

type MessageDto = {
  id: number;
  content: string;
  senderId: number;
  senderUsername: string;
  recipientId: number | null;
  timestamp: string;
  messageType: string;
  isDeleted?: boolean;
};

type OnlineUserStatus = {
  userid: number;
  status: string;
  timestamp: string;
  username: string;
};

interface WebSocketContextType {
  connected: boolean;
  publicMessages: MessageDto[];
  privateMessages: Record<number, MessageDto[]>;
  onlineUsers: Record<number, { status: string; username: string; timestamp: string }>;
  sendPublicMessage: (content: string) => void;
  sendPrivateMessage: (recipientId: number, content: string) => void;
  sendStatusUpdate: (status: string) => void;
  loadPublicHistory: () => Promise<void>;
  loadPrivateHistory: (contactUserId: number) => Promise<void>;
  hasMorePublicHistory: boolean;
  hasMorePrivateHistory: Record<number, boolean>;
}

const WebSocketContext = createContext<WebSocketContextType | null>(null);

export const WebSocketProvider = ({ children }: { children: React.ReactNode }) => {
  const { token, isAuthenticated, userId } = useAuth();
  const [connected, setConnected] = useState(false);
  const [publicMessages, setPublicMessages] = useState<MessageDto[]>([]);
  const [privateMessages, setPrivateMessages] = useState<Record<number, MessageDto[]>>({});
  const [onlineUsers, setOnlineUsers] = useState<Record<number, { status: string; username: string; timestamp: string }>>({});
  
  const [hasMorePublicHistory, setHasMorePublicHistory] = useState(true);
  const [hasMorePrivateHistory, setHasMorePrivateHistory] = useState<Record<number, boolean>>({});
  const [publicCursor, setPublicCursor] = useState<string | null>(null);
  const [privateCursors, setPrivateCursors] = useState<Record<number, string | null>>({});

  const stompClientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
      setTimeout(() => {
        setConnected(false);
        setPublicMessages([]);
        setPrivateMessages({});
        setOnlineUsers({});
        setHasMorePublicHistory(true);
        setHasMorePrivateHistory({});
        setPublicCursor(null);
        setPrivateCursors({});
      }, 0);
      return;
    }

    setTimeout(() => {
      setPublicMessages([]);
      setPrivateMessages({});
      setOnlineUsers({});
      setHasMorePublicHistory(true);
      setHasMorePrivateHistory({});
      setPublicCursor(null);
      setPrivateCursors({});
    }, 0);

    // Connect to Backend WebSocket
    const socketUrl = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8181/ws";
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        console.log("STOMP: " + str);
      },
      onStompError: (frame) => {
        console.error("Broker reported error: " + frame.headers["message"]);
        console.error("Additional details: " + frame.body);
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      console.log("Connected to WebSocket Broker");
      setConnected(true);

      // 1. Subscribe to Public Topic
      client.subscribe("/topic/public", (message) => {
        const msg: MessageDto = JSON.parse(message.body);
        setPublicMessages((prev) => {
          const exists = prev.some((p) => 
            p.id === msg.id || 
            (p.senderId === msg.senderId && 
             new Date(p.timestamp).getTime() === new Date(msg.timestamp).getTime())
          );
          if (exists) {
            return prev.map((p) => {
              if (p.id === msg.id || (p.senderId === msg.senderId && new Date(p.timestamp).getTime() === new Date(msg.timestamp).getTime())) {
                return msg;
              }
              return p;
            });
          }
          return [...prev, msg];
        });
      });

      // 2. Subscribe to Private Queue (regular and requests)
      client.subscribe("/user/queue/messages", (message) => {
        const msg: MessageDto = JSON.parse(message.body);
        const partnerId = msg.senderId === userId ? msg.recipientId : msg.senderId;
        if (!partnerId) return;

        setPrivateMessages((prev) => {
          const current = prev[partnerId] || [];
          const exists = current.some((c) => 
            c.id === msg.id || 
            (c.senderId === msg.senderId && 
             new Date(c.timestamp).getTime() === new Date(msg.timestamp).getTime())
          );
          if (exists) {
            const updatedList = current.map((c) => {
              if (c.id === msg.id || (c.senderId === msg.senderId && new Date(c.timestamp).getTime() === new Date(msg.timestamp).getTime())) {
                return msg;
              }
              return c;
            });
            return {
              ...prev,
              [partnerId]: updatedList,
            };
          }
          return {
            ...prev,
            [partnerId]: [...current, msg],
          };
        });

        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("message:received", { detail: msg }));
        }
      });

      client.subscribe("/user/queue/requests", (message) => {
        // First message from a new contact will trigger request banner
        const msg: MessageDto = JSON.parse(message.body);
        const partnerId = msg.senderId === userId ? msg.recipientId : msg.senderId;
        if (!partnerId) return;

        setPrivateMessages((prev) => {
          const current = prev[partnerId] || [];
          const exists = current.some((c) => 
            c.id === msg.id || 
            (c.senderId === msg.senderId && 
             new Date(c.timestamp).getTime() === new Date(msg.timestamp).getTime())
          );
          if (exists) {
            const updatedList = current.map((c) => {
              if (c.id === msg.id || (c.senderId === msg.senderId && new Date(c.timestamp).getTime() === new Date(msg.timestamp).getTime())) {
                return msg;
              }
              return c;
            });
            return {
              ...prev,
              [partnerId]: updatedList,
            };
          }
          return {
            ...prev,
            [partnerId]: [...current, msg],
          };
        });

        // Notify Sidebar/Dashboard to reload contacts/requests
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("contacts:updated"));
          window.dispatchEvent(new CustomEvent("message:received", { detail: msg }));
        }
      });

      // 3. Subscribe to Online Notification Topic
      client.subscribe("/topic/online", (message) => {
        const statusUpdate: OnlineUserStatus = JSON.parse(message.body);
        setOnlineUsers((prev) => ({
          ...prev,
          [statusUpdate.userid]: {
            status: statusUpdate.status,
            username: statusUpdate.username,
            timestamp: statusUpdate.timestamp,
          },
        }));
      });

      // Automatically publish ONLINE status
      client.publish({
        destination: "/app/noti.status",
        body: JSON.stringify({ status: "ONLINE" }),
      });

      // Fetch initially online users from REST API
      apiFetch("/api/v1/users/online")
        .then((onlineList: any) => {
          if (Array.isArray(onlineList)) {
            setOnlineUsers((prev) => {
              const newOnline = { ...prev };
              onlineList.forEach((user: any) => {
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
          console.error("Failed to fetch online users:", err);
        });
    };

    client.onDisconnect = () => {
      console.log("Disconnected from WebSocket Broker");
      setConnected(false);
    };

    client.activate();
    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
        setConnected(false);
      }
    };
  }, [token, isAuthenticated, userId]);

  useEffect(() => {
    const handleChatDeleted = (event: Event) => {
      const customEvent = event as CustomEvent;
      const { contactUserId } = customEvent.detail;
      setPrivateMessages((prev) => ({
        ...prev,
        [contactUserId]: [],
      }));
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

      // Optimistically insert sent message into sender's private conversation array
      // (Wait, backend ChatController echo-s back to /user/queue/messages for the sender anyway,
      // but doing it here or waiting for the echo is fine. ChatController does send back to sender's own queue:
      // messagingTemplate.convertAndSendToUser(senderId.toString(), "/queue/messages", messageDto);
      // So the client receives it on /user/queue/messages, we don't need manual duplicate insertion.)
    }
  };

  const sendStatusUpdate = (status: string) => {
    if (stompClientRef.current && connected) {
      stompClientRef.current.publish({
        destination: "/app/noti.status",
        body: JSON.stringify({ status }),
      });
    }
  };

  const loadPublicHistory = async () => {
    if (!hasMorePublicHistory) return;

    try {
      const url = `/api/v1/messages/public?limit=30${publicCursor ? `&cursor=${encodeURIComponent(publicCursor)}` : ""}`;
      const data = await apiFetch(url);
      
      const messages = data.messages || [];
      const nextCursor = data.nextCursor;
      
      if (!nextCursor || messages.length === 0) {
        setHasMorePublicHistory(false);
      } else {
        setPublicCursor(nextCursor);
      }

      setPublicMessages((prev) => {
        // Merge histories and sort chronologically (ascending)
        const combined = [...messages, ...prev];
        const unique = combined.filter((m: MessageDto, idx: number, self: MessageDto[]) => 
          self.findIndex((x) => 
            x.id === m.id || 
            (x.senderId === m.senderId && 
             x.content === m.content && 
             new Date(x.timestamp).getTime() === new Date(m.timestamp).getTime())
          ) === idx
        );
        return unique.sort((a: MessageDto, b: MessageDto) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
      });
    } catch (err) {
      console.error("Error loading public history:", err);
    }
  };

  const loadPrivateHistory = async (contactUserId: number) => {
    if (hasMorePrivateHistory[contactUserId] === false) return;
    const currentCursor = privateCursors[contactUserId];

    try {
      // NOTE: We assume the backend for private history will also be updated to return MessagePage.
      // If it hasn't been updated yet, we will just use the currentCursor as 'cursor' and hope the backend accepts it.
      // Since we haven't updated MessageController.getPrivateMessages yet, we should probably do that too.
      // Let's assume it works exactly the same.
      const url = `/api/v1/messages/private/${contactUserId}?limit=30${currentCursor ? `&cursor=${encodeURIComponent(currentCursor)}` : ""}`;
      const data = await apiFetch(url);

      // If backend returns a plain array, it won't have .messages. Let's handle both.
      const messages = data.messages || (Array.isArray(data) ? data : []);
      const nextCursor = data.nextCursor || null;

      if (!nextCursor || messages.length === 0) {
        setHasMorePrivateHistory((prev) => ({ ...prev, [contactUserId]: false }));
      } else {
        setPrivateCursors((prev) => ({ ...prev, [contactUserId]: nextCursor }));
      }

      setPrivateMessages((prev) => {
        const current = prev[contactUserId] || [];
        const combined = [...messages, ...current];
        const unique = combined.filter((m: MessageDto, idx: number, self: MessageDto[]) => 
          self.findIndex((x) => 
            x.id === m.id || 
            (x.senderId === m.senderId && 
             x.content === m.content && 
             new Date(x.timestamp).getTime() === new Date(m.timestamp).getTime())
          ) === idx
        );
        const sorted = unique.sort((a: MessageDto, b: MessageDto) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
        return {
          ...prev,
          [contactUserId]: sorted,
        };
      });
    } catch (err) {
      console.error(`Error loading private history for ${contactUserId}:`, err);
    }
  };

  return (
    <WebSocketContext.Provider
      value={{
        connected,
        publicMessages,
        privateMessages,
        onlineUsers,
        sendPublicMessage,
        sendPrivateMessage,
        sendStatusUpdate,
        loadPublicHistory,
        loadPrivateHistory,
        hasMorePublicHistory,
        hasMorePrivateHistory,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
};
