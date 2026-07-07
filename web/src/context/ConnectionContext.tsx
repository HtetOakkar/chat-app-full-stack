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
import { Client, IMessage, StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

export interface ConnectionContextType {
  connected: boolean;
  stompClientRef: React.MutableRefObject<Client | null>;
  subscribe: (
    destination: string,
    callback: (message: IMessage) => void
  ) => () => void;
}

const ConnectionContext = createContext<ConnectionContextType | null>(null);

export const ConnectionProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const { token, isAuthenticated } = useAuth();
  const [connected, setConnected] = useState(false);
  const stompClientRef = useRef<Client | null>(null);

  // Keep track of active subscriptions to auto-resubscribe on reconnect
  const subscriptionsRef = useRef<
    Map<
      string,
      {
        destination: string;
        callback: (message: IMessage) => void;
        stompSubscription?: StompSubscription;
      }
    >
  >(new Map());

  // Subscription helper
  const subscribe = useCallback(
    (destination: string, callback: (message: IMessage) => void) => {
      const id = Math.random().toString(36).substring(2, 9);
      const subObj: {
        destination: string;
        callback: (message: IMessage) => void;
        stompSubscription?: StompSubscription;
      } = {
        destination,
        callback,
      };
      subscriptionsRef.current.set(id, subObj);

      // If already connected, perform the subscription immediately
      if (stompClientRef.current && stompClientRef.current.connected) {
        try {
          subObj.stompSubscription = stompClientRef.current.subscribe(
            destination,
            callback
          );
        } catch (err) {
          console.error(
            `STOMP subscription failed immediately for ${destination}:`,
            err
          );
        }
      }

      return () => {
        const existing = subscriptionsRef.current.get(id);
        if (existing) {
          if (existing.stompSubscription) {
            try {
              existing.stompSubscription.unsubscribe();
            } catch (err) {
              console.error(
                `STOMP unsubscribe failed for ${destination}:`,
                err
              );
            }
          }
          subscriptionsRef.current.delete(id);
        }
      };
    },
    []
  );

  useEffect(() => {
    if (!isAuthenticated || !token) {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
      // Defer state update to avoid cascading render warning in useEffect
      const handle = setTimeout(() => {
        setConnected(false);
      }, 0);
      return () => clearTimeout(handle);
    }

    // Connect to Backend WebSocket
    let socketUrl = process.env.NEXT_PUBLIC_WS_URL;
    if (!socketUrl) {
      if (typeof window !== "undefined" && window.location) {
        socketUrl = `${window.location.protocol}//${window.location.hostname}:8181/ws`;
      } else {
        socketUrl = "http://localhost:8181/ws";
      }
    }
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        console.log("STOMP: " + str);
      },
      onStompError: (frame) => {
        if (stompClientRef.current !== client) return;
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
      if (stompClientRef.current !== client) return;
      console.log("Connected to WebSocket Broker");
      setConnected(true);

      // Re-subscribe all active subscriptions
      subscriptionsRef.current.forEach((subObj) => {
        try {
          subObj.stompSubscription = client.subscribe(
            subObj.destination,
            subObj.callback
          );
        } catch (err) {
          console.error(
            `STOMP re-subscription failed for ${subObj.destination}:`,
            err
          );
        }
      });
    };

    client.onDisconnect = () => {
      if (stompClientRef.current !== client) return;
      console.log("Disconnected from WebSocket Broker");
      setConnected(false);
    };

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
      if (stompClientRef.current === client) {
        stompClientRef.current = null;
        setConnected(false);
      }
    };
  }, [token, isAuthenticated]);

  return (
    <ConnectionContext.Provider
      value={{
        connected,
        stompClientRef,
        subscribe,
      }}
    >
      {children}
    </ConnectionContext.Provider>
  );
};

export const useConnection = () => {
  const context = useContext(ConnectionContext);
  if (!context) {
    throw new Error("useConnection must be used within a ConnectionProvider");
  }
  return context;
};
