"use client";

import { useState, useEffect, useRef } from "react";
import { apiFetch } from "@/lib/api";
import { useWebSocket } from "@/context/WebSocketContext";
import { useAuth } from "@/context/AuthContext";

type Contact = {
  id: number;
  contactUserId: number;
  contactUsername: string;
  status: string;
  createdAt: string;
  lastMessageContent?: string | null;
  lastMessageTimestamp?: string | null;
  lastMessageSenderId?: number | null;
  unreadCount?: number | null;
};

type UserDto = {
  id: number;
  username: string;
  fullName?: string;
  createdAt: string;
};

export type ActiveChat = {
  id: number;
  username: string;
  isPublic: boolean;
  status?: string;
};

interface SidebarProps {
  activeChat: ActiveChat | null;
  onSelectChat: (chat: ActiveChat) => void;
  onSelectProfileUser: (user: {
    id: number;
    username: string;
    status?: string;
  }) => void;
  refreshTrigger: number;
  viewMode?: "chat" | "profile" | "user-profile";
}

export default function Sidebar({
  activeChat,
  onSelectChat,
  onSelectProfileUser,
  refreshTrigger,
  viewMode = "chat",
}: SidebarProps) {
  const { onlineUsers } = useWebSocket();
  const { userId: currentUserId } = useAuth();

  const [contacts, setContacts] = useState<Contact[]>([]);
  const [requests, setRequests] = useState<Contact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);
  const [sidebarView, setSidebarView] = useState<
    "chats" | "contacts" | "requests"
  >("chats");
  const [contactsFilter, setContactsFilter] = useState<"CONTACT" | "BLOCKED">(
    "CONTACT"
  );

  // Search state
  const [searchQuery, setSearchQuery] = useState("");
  const [contactsSearchQuery, setContactsSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<UserDto[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchFocused, setSearchFocused] = useState(false);
  const [addingContact, setAddingContact] = useState<number | null>(null);
  const searchTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const [activeMenuContactId, setActiveMenuContactId] = useState<number | null>(
    null
  );

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
      fetchData();
    } catch (err) {
      console.error("Failed to delete chat:", err);
    } finally {
      setActiveMenuContactId(null);
    }
  };

  // Fetch contacts and requests
  const fetchData = async () => {
    try {
      setLoadingContacts(true);
      const [contactsData, requestsData, blockedData] = await Promise.all([
        apiFetch("/api/v1/contacts"),
        apiFetch("/api/v1/contacts/requests"),
        apiFetch("/api/v1/contacts/blocked"),
      ]);
      setContacts([...(contactsData || []), ...(blockedData || [])]);
      setRequests(requestsData || []);
    } catch {
      // Silent fail — contacts will show as empty
    } finally {
      setLoadingContacts(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [refreshTrigger]);

  useEffect(() => {
    const handleContactsUpdate = () => {
      fetchData();
    };

    window.addEventListener("contacts:updated", handleContactsUpdate);
    return () => {
      window.removeEventListener("contacts:updated", handleContactsUpdate);
    };
  }, []);

  useEffect(() => {
    if (activeChat) {
      setSidebarView("chats");
    }
  }, [activeChat?.id, activeChat?.isPublic]);

  // Close contact settings menu on outside clicks
  useEffect(() => {
    const handleWindowClick = () => {
      setActiveMenuContactId(null);
    };
    if (activeMenuContactId !== null) {
      window.addEventListener("click", handleWindowClick);
    }
    return () => {
      window.removeEventListener("click", handleWindowClick);
    };
  }, [activeMenuContactId]);

  // Clear unread count locally when activeChat changes to a direct message chat
  useEffect(() => {
    if (activeChat && !activeChat.isPublic) {
      setContacts((prev) =>
        prev.map((c) =>
          c.contactUserId === activeChat.id ? { ...c, unreadCount: 0 } : c
        )
      );
    }
  }, [activeChat?.id, activeChat?.isPublic]);

  // Auto-switch sidebar view back to chats if activeChat is accepted (not pending/neglected)
  const prevActiveChatRef = useRef<ActiveChat | null>(null);
  useEffect(() => {
    if (
      sidebarView === "requests" &&
      activeChat &&
      prevActiveChatRef.current &&
      activeChat.id === prevActiveChatRef.current.id &&
      (prevActiveChatRef.current.status === "PENDING_REQUEST" ||
        prevActiveChatRef.current.status === "NEGLECTED") &&
      activeChat.status !== "PENDING_REQUEST" &&
      activeChat.status !== "NEGLECTED"
    ) {
      setSidebarView("chats");
    }
    prevActiveChatRef.current = activeChat;
  }, [activeChat, sidebarView]);

  // Listen for WebSocket real-time messages to update unread counts and last message previews
  useEffect(() => {
    const handleMessageReceived = (event: Event) => {
      const customEvent = event as CustomEvent;
      const msg = customEvent.detail;
      if (!msg) return;

      const partnerId =
        msg.senderId === currentUserId ? msg.recipientId : msg.senderId;

      setContacts((prevContacts) => {
        const exists = prevContacts.some((c) => c.contactUserId === partnerId);
        if (!exists && partnerId) {
          // Trigger reload from server so the contact is restored in the sidebar
          setTimeout(() => {
            fetchData();
          }, 50);
          return prevContacts;
        }

        return prevContacts.map((contact) => {
          const isSender = msg.senderId === contact.contactUserId;
          const isRecipient = msg.recipientId === contact.contactUserId;
          if (isSender || isRecipient) {
            const updatedContact = {
              ...contact,
              lastMessageContent: msg.content,
              lastMessageTimestamp: msg.timestamp,
              lastMessageSenderId: msg.senderId,
            };
            const isActive =
              activeChat &&
              !activeChat.isPublic &&
              activeChat.id === contact.contactUserId;
            if (isSender && !isActive) {
              updatedContact.unreadCount = (contact.unreadCount || 0) + 1;
            }
            return updatedContact;
          }
          return contact;
        });
      });
    };

    window.addEventListener("message:received", handleMessageReceived);
    return () => {
      window.removeEventListener("message:received", handleMessageReceived);
    };
  }, [activeChat]);

  // Debounced search
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }

    if (searchQuery.trim().length < 2) {
      setSearchResults([]);
      return;
    }

    searchTimeoutRef.current = setTimeout(async () => {
      try {
        setSearchLoading(true);
        const data = await apiFetch(
          `/api/v1/users/search?keyword=${encodeURIComponent(searchQuery)}`
        );
        setSearchResults(data || []);
      } catch {
        setSearchResults([]);
      } finally {
        setSearchLoading(false);
      }
    }, 400);

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
  }, [searchQuery]);

  const handleAddContact = async (username: string, userId: number) => {
    try {
      setAddingContact(userId);
      await apiFetch("/api/v1/contacts", {
        method: "POST",
        body: JSON.stringify({ username }),
      });
      fetchData();
      setSearchQuery("");
      setSearchResults([]);
    } catch {
      // Silent fail
    } finally {
      setAddingContact(null);
    }
  };

  const handleAcceptRequest = async (userId: number) => {
    try {
      await apiFetch(`/api/v1/contacts/${userId}/accept`, {
        method: "PUT",
      });
      fetchData();
    } catch {
      // Silent fail
    }
  };

  const showSearchOverlay = searchFocused && searchQuery.trim().length >= 2;

  return (
    <aside
      className={`${
        activeChat === null && viewMode === "chat" ? "flex w-full" : "hidden"
      } md:flex flex-col h-full w-80 lg:w-96 bg-surface-container-low/60 border-r border-outline-variant/10 shrink-0`}
    >
      {/* Header */}
      <div className="px-5 pt-6 pb-4 shrink-0">
        <div className="flex items-center gap-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center shadow-sm shadow-primary/20">
            <span
              className="material-symbols-outlined text-white text-xl"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              forum
            </span>
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-black text-on-surface font-headline tracking-tight leading-tight truncate">
              Meow Chit Chat
            </h2>
            <p className="text-[9px] text-outline font-bold uppercase tracking-[0.15em] truncate">
              {contacts.length + requests.length} Connections
            </p>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex bg-surface-container-lowest rounded-lg p-1 mb-5 border border-outline-variant/20">
          <button
            type="button"
            onClick={() => setSidebarView("chats")}
            className={`flex-1 text-xs font-bold py-1.5 rounded-md transition-colors ${sidebarView === "chats" ? "bg-primary text-white shadow-sm shadow-primary/20" : "text-outline hover:text-on-surface"}`}
          >
            Chats
          </button>
          <button
            type="button"
            onClick={() => setSidebarView("contacts")}
            className={`flex-1 text-xs font-bold py-1.5 rounded-md transition-colors ${sidebarView === "contacts" ? "bg-primary text-white shadow-sm shadow-primary/20" : "text-outline hover:text-on-surface"}`}
          >
            Contacts
          </button>
          <button
            type="button"
            onClick={() => setSidebarView("requests")}
            className={`flex-1 text-xs font-bold py-1.5 rounded-md transition-colors flex items-center justify-center gap-1 ${sidebarView === "requests" ? "bg-primary text-white shadow-sm shadow-primary/20" : "text-outline hover:text-on-surface"}`}
          >
            Requests
            {requests.length > 0 && (
              <span className="w-4 h-4 rounded-full bg-secondary text-white text-[9px] font-bold flex items-center justify-center">
                {requests.length}
              </span>
            )}
          </button>
        </div>

        {/* Global Search Input */}
        <div className="relative px-4 pb-2">
          <div className="relative">
            <div className="flex items-center bg-surface-container-lowest rounded-xl border border-transparent focus-within:border-primary/30 transition-all shadow-sm">
              <span className="material-symbols-outlined text-outline text-lg ml-3">
                search
              </span>
              <input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => setSearchFocused(true)}
                onBlur={() => setTimeout(() => setSearchFocused(false), 200)}
                className="flex-1 bg-transparent border-none outline-none focus:ring-0 text-xs px-3 py-3 text-on-surface placeholder:text-outline"
                placeholder="Search global registry..."
                type="text"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery("");
                    setSearchResults([]);
                  }}
                  className="p-1.5 mr-1.5 text-outline hover:text-on-surface rounded-full hover:bg-surface-container-high transition-colors"
                >
                  <span className="material-symbols-outlined text-sm">
                    close
                  </span>
                </button>
              )}
            </div>

            {/* Search Results Overlay */}
            {showSearchOverlay && (
              <div className="absolute top-full left-0 right-0 mt-2 bg-surface-container-lowest rounded-xl border border-outline-variant/20 shadow-[0_16px_48px_rgba(0,0,0,0.12)] z-50 max-h-64 overflow-y-auto custom-scrollbar">
                {searchLoading ? (
                  <div className="flex items-center justify-center py-6">
                    <span className="material-symbols-outlined animate-spin text-primary text-lg">
                      rotate_right
                    </span>
                  </div>
                ) : searchResults.length === 0 ? (
                  <div className="py-6 text-center text-xs text-outline">
                    No users found for &quot;{searchQuery}&quot;
                  </div>
                ) : (
                  searchResults.map((user) => {
                    const isContact = contacts.find(
                      (c) => c.contactUserId === user.id
                    );
                    const isPending = requests.find(
                      (r) => r.contactUserId === user.id
                    );
                    const status = isContact
                      ? "CONTACT"
                      : isPending
                        ? "PENDING_REQUEST"
                        : undefined;
                    return (
                      <div
                        key={user.id}
                        onClick={() =>
                          onSelectProfileUser({
                            id: user.id,
                            username: user.username,
                            status,
                          })
                        }
                        className="flex items-center justify-between px-4 py-3 hover:bg-surface-container-low/50 transition-colors border-b border-outline-variant/5 last:border-0 cursor-pointer"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg bg-primary/5 flex items-center justify-center text-xs font-extrabold text-primary border border-primary/10">
                            {user.username.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className="text-xs font-bold text-on-surface">
                              {user.fullName
                                ? `${user.fullName} (${user.username})`
                                : user.username}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Scrollable Content */}
      <div className="flex-1 overflow-y-auto custom-scrollbar px-3 pb-4 space-y-1">
        {sidebarView === "chats" && (
          <>
            {/* Section Label */}
            <div className="px-2 pt-2 pb-1.5">
              <span className="text-[9px] font-bold uppercase tracking-[0.15em] text-outline">
                Channels
              </span>
            </div>

            {/* Global Public Chat */}
            <button
              type="button"
              onClick={() =>
                onSelectChat({
                  id: 0,
                  username: "Global Registry Chat",
                  isPublic: true,
                })
              }
              className={`w-full flex items-center gap-3 px-3 py-3 rounded-xl transition-all duration-200 text-left ${
                activeChat?.isPublic
                  ? "bg-primary/8 border border-primary/15"
                  : "hover:bg-surface-container-lowest/60 border border-transparent"
              }`}
            >
              <div
                className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                  activeChat?.isPublic
                    ? "bg-primary text-white shadow-sm shadow-primary/20"
                    : "bg-primary/10 text-primary"
                }`}
              >
                <span className="material-symbols-outlined text-lg">
                  language
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <h4 className="font-bold text-xs text-on-surface leading-tight truncate">
                  Global Registry Chat
                </h4>
                <p className="text-[9px] text-outline font-medium mt-0.5 truncate">
                  Public channel for all curators
                </p>
              </div>
              <span className="px-1.5 py-0.5 rounded text-[7px] font-bold tracking-wider uppercase bg-primary/10 text-primary shrink-0">
                Public
              </span>
            </button>

            {/* Contacts Section */}
            <div className="px-2 pt-4 pb-1.5">
              <span className="text-[9px] font-bold uppercase tracking-[0.15em] text-outline">
                Contacts
              </span>
            </div>

            {loadingContacts ? (
              <div className="flex justify-center py-8">
                <span className="material-symbols-outlined animate-spin text-xl text-primary">
                  rotate_right
                </span>
              </div>
            ) : contacts.filter((c) => c.status !== "BLOCKED").length === 0 ? (
              <div className="text-center py-8 px-4">
                <span className="material-symbols-outlined text-outline/30 text-2xl mb-2 block">
                  person_search
                </span>
                <p className="text-[10px] text-outline leading-relaxed">
                  No contacts yet. Use the search bar above to discover
                  curators.
                </p>
              </div>
            ) : (
              (() => {
                const sortedContacts = [...contacts]
                  .filter((c) => c.status !== "BLOCKED")
                  .sort((a, b) => {
                    const timeA = a.lastMessageTimestamp
                      ? new Date(a.lastMessageTimestamp).getTime()
                      : new Date(a.createdAt).getTime();
                    const timeB = b.lastMessageTimestamp
                      ? new Date(b.lastMessageTimestamp).getTime()
                      : new Date(b.createdAt).getTime();
                    return timeB - timeA;
                  });
                return sortedContacts.map((contact) => {
                  const isSelected =
                    activeChat &&
                    !activeChat.isPublic &&
                    activeChat.id === contact.contactUserId;
                  const userStatus =
                    onlineUsers[contact.contactUserId]?.status || "OFFLINE";

                  return (
                    <div
                      role="button"
                      tabIndex={0}
                      key={contact.id}
                      onClick={() =>
                        onSelectChat({
                          id: contact.contactUserId,
                          username: contact.contactUsername,
                          isPublic: false,
                          status: contact.status,
                        })
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          onSelectChat({
                            id: contact.contactUserId,
                            username: contact.contactUsername,
                            isPublic: false,
                            status: contact.status,
                          });
                        }
                      }}
                      className={`w-full flex items-center gap-3 px-3 py-3 rounded-xl transition-all duration-200 text-left cursor-pointer group relative ${
                        isSelected
                          ? "bg-primary/8 border border-primary/15"
                          : "hover:bg-surface-container-lowest/60 border border-transparent"
                      }`}
                    >
                      <div className="relative">
                        <div className="w-9 h-9 rounded-lg bg-primary/5 flex items-center justify-center text-xs font-extrabold text-primary border border-primary/10">
                          {contact.contactUsername.charAt(0).toUpperCase()}
                        </div>
                        {/* Online indicator */}
                        <div
                          className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 border-2 border-surface-container-low rounded-full ${
                            userStatus === "ONLINE"
                              ? "bg-tertiary"
                              : "bg-outline/30"
                          }`}
                        />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-1">
                          <h4 className="font-bold text-xs text-on-surface leading-tight truncate">
                            {contact.contactUsername}
                          </h4>
                          {contact.lastMessageTimestamp && (
                            <span className="text-[8px] text-outline shrink-0">
                              {new Date(
                                contact.lastMessageTimestamp
                              ).toLocaleTimeString([], {
                                hour: "2-digit",
                                minute: "2-digit",
                              })}
                            </span>
                          )}
                        </div>
                        <div className="flex items-center justify-between gap-1 mt-1">
                          <p className="text-[10px] text-outline truncate flex-1 min-w-0 font-medium">
                            {contact.lastMessageContent
                              ? contact.lastMessageSenderId === currentUserId
                                ? `You: ${contact.lastMessageContent}`
                                : contact.lastMessageContent
                              : `${userStatus.toLowerCase()}`}
                          </p>
                          <div className="flex items-center gap-1 shrink-0">
                            {contact.unreadCount && contact.unreadCount > 0 ? (
                              <span className="w-4.5 h-4.5 rounded-full bg-primary text-white text-[9px] font-black flex items-center justify-center shrink-0 shadow-sm shadow-primary/20">
                                {contact.unreadCount}
                              </span>
                            ) : null}
                            {/* Chat options button visible on hover/focus */}
                            <div className="relative">
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setActiveMenuContactId(
                                    activeMenuContactId ===
                                      contact.contactUserId
                                      ? null
                                      : contact.contactUserId
                                  );
                                }}
                                className="opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity p-0.5 text-outline hover:text-on-surface rounded-full hover:bg-surface-container-high flex items-center justify-center"
                                title="Chat options"
                              >
                                <span className="material-symbols-outlined text-sm">
                                  more_vert
                                </span>
                              </button>
                              {activeMenuContactId ===
                                contact.contactUserId && (
                                <div className="absolute right-0 top-full mt-1 z-35 bg-surface-container-lowest border border-outline-variant/20 rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.08)] py-1.5 min-w-[130px] animate-[fadeIn_0.15s_ease-out]">
                                  <button
                                    type="button"
                                    onClick={() =>
                                      handleDeleteChat(contact.contactUserId)
                                    }
                                    className="w-full text-left px-3.5 py-2 text-xs text-error hover:bg-surface-container-low transition-colors flex items-center gap-1.5 font-bold uppercase tracking-wider"
                                  >
                                    <span className="material-symbols-outlined text-sm text-error">
                                      delete
                                    </span>
                                    Delete Chat
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                });
              })()
            )}
          </>
        )}
        {sidebarView === "contacts" && (
          <div className="space-y-1 animate-[fadeIn_0.2s_ease-out]">
            <div className="px-2 pt-2 pb-3 flex flex-col gap-2">
              <input
                type="text"
                placeholder="Search contacts..."
                value={contactsSearchQuery}
                onChange={(e) => setContactsSearchQuery(e.target.value)}
                className="bg-surface-container-lowest rounded-lg text-xs px-3 py-2 text-on-surface placeholder:text-outline border border-outline-variant/30 focus:border-primary/50 outline-none transition-all"
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setContactsFilter("CONTACT")}
                  className={`px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide transition-colors ${contactsFilter === "CONTACT" ? "bg-primary/10 text-primary" : "border border-outline-variant/30 text-outline hover:text-on-surface"}`}
                >
                  All Contacts
                </button>
                <button
                  type="button"
                  onClick={() => setContactsFilter("BLOCKED")}
                  className={`px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide transition-colors ${contactsFilter === "BLOCKED" ? "bg-primary/10 text-primary" : "border border-outline-variant/30 text-outline hover:text-on-surface"}`}
                >
                  Blocked
                </button>
              </div>
            </div>

            {loadingContacts ? (
              <div className="flex justify-center py-8">
                <span className="material-symbols-outlined animate-spin text-xl text-primary">
                  rotate_right
                </span>
              </div>
            ) : contacts.filter(
                (c) =>
                  c.status === contactsFilter &&
                  c.contactUsername
                    .toLowerCase()
                    .includes(contactsSearchQuery.toLowerCase())
              ).length === 0 ? (
              <div className="text-center py-8 px-4">
                <p className="text-[10px] text-outline">No contacts found.</p>
              </div>
            ) : (
              contacts
                .filter(
                  (c) =>
                    c.status === contactsFilter &&
                    c.contactUsername
                      .toLowerCase()
                      .includes(contactsSearchQuery.toLowerCase())
                )
                .map((contact) => (
                  <div
                    key={`contact-${contact.id}`}
                    onClick={() =>
                      onSelectProfileUser({
                        id: contact.contactUserId,
                        username: contact.contactUsername,
                        status: contact.status,
                      })
                    }
                    className="w-full flex items-center gap-3 px-3 py-3 rounded-xl transition-all duration-200 text-left cursor-pointer hover:bg-surface-container-lowest/60 border border-transparent"
                  >
                    <div className="relative">
                      <div className="w-9 h-9 rounded-lg bg-primary/5 flex items-center justify-center text-xs font-extrabold text-primary border border-primary/10">
                        {contact.contactUsername.charAt(0).toUpperCase()}
                      </div>
                      <div
                        className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 border-2 border-surface-container-low rounded-full ${onlineUsers[contact.contactUserId]?.status === "ONLINE" ? "bg-tertiary" : "bg-outline/30"}`}
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <h4 className="font-bold text-xs text-on-surface leading-tight truncate">
                        {contact.contactUsername}
                      </h4>
                    </div>
                  </div>
                ))
            )}
          </div>
        )}
        {sidebarView === "requests" && (
          <div className="space-y-1 animate-[fadeIn_0.2s_ease-out]">
            {/* Section Label */}
            <div className="px-2 pt-2 pb-1.5 flex items-center gap-2">
              <span className="text-[9px] font-bold uppercase tracking-[0.15em] text-outline">
                Pending Requests
              </span>
            </div>

            {loadingContacts ? (
              <div className="flex justify-center py-8">
                <span className="material-symbols-outlined animate-spin text-xl text-primary">
                  rotate_right
                </span>
              </div>
            ) : requests.length === 0 ? (
              <div className="text-center py-12 px-4 flex flex-col items-center justify-center">
                <span className="material-symbols-outlined text-outline/30 text-3xl mb-2">
                  person_add_disabled
                </span>
                <p className="text-[10px] text-outline leading-relaxed">
                  No pending message requests.
                </p>
              </div>
            ) : (
              requests.map((request) => {
                const isSelected =
                  activeChat &&
                  !activeChat.isPublic &&
                  activeChat.id === request.contactUserId;
                return (
                  <button
                    type="button"
                    key={`req-${request.id}`}
                    onClick={() =>
                      onSelectChat({
                        id: request.contactUserId,
                        username: request.contactUsername,
                        isPublic: false,
                        status: "PENDING_REQUEST",
                      })
                    }
                    className={`w-full flex items-center gap-3 px-3 py-3 rounded-xl transition-all duration-200 text-left border ${
                      isSelected
                        ? "bg-secondary/8 border-secondary/15"
                        : "hover:bg-surface-container-lowest/60 border border-transparent"
                    }`}
                  >
                    <div className="relative">
                      <div className="w-9 h-9 rounded-lg bg-secondary/10 flex items-center justify-center text-xs font-extrabold text-secondary border border-secondary/15">
                        {request.contactUsername.charAt(0).toUpperCase()}
                      </div>
                    </div>
                    <div className="min-w-0 flex-1">
                      <h4 className="font-bold text-xs text-on-surface leading-tight truncate">
                        {request.contactUsername}
                      </h4>
                      <p className="text-[9px] text-outline mt-0.5">
                        Wants to chat with you
                      </p>
                    </div>
                    <span className="px-1.5 py-0.5 rounded text-[7px] font-bold tracking-wider uppercase bg-secondary/10 text-secondary shrink-0 animate-pulse">
                      Pending
                    </span>
                  </button>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="px-4 py-3 border-t border-outline-variant/10 shrink-0">
        <div className="text-[8px] text-center text-outline/40 uppercase tracking-[0.15em] font-label">
          Meow Chit Chat v2026.1
        </div>
      </div>
    </aside>
  );
}
