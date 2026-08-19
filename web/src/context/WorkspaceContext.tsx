"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
} from "react";
import { useAuth } from "./AuthContext";
import { useConnection } from "./ConnectionContext";
import { apiFetch } from "@/lib/api";

export interface WorkspaceDto {
  id: number;
  name: string;
  slug: string;
  iconUrl?: string | null;
  ownerId: number;
  role: "OWNER" | "ADMIN" | "MEMBER";
  memberCount: number;
  channelCount: number;
  createdAt: string;
}

export interface ChannelDto {
  id: number;
  workspaceId: number;
  conversationId: number;
  name: string;
  slug: string;
  description?: string;
  type: "PUBLIC" | "PRIVATE";
  isArchived: boolean;
  isMember: boolean;
  memberCount: number;
  createdAt: string;
}

export interface WorkspaceMemberDto {
  id: number;
  workspaceId: number;
  userId: number;
  username: string;
  fullName?: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  joinedAt: string;
  presenceStatus?: "ONLINE" | "OFFLINE";
}

export interface DirectMessageDto {
  conversationId: number;
  workspaceId: number;
  targetMember: WorkspaceMemberDto;
  unreadCount?: number;
  createdAt: string;
}

export interface BatchInvitationItemResult {
  email: string;
  status: "sent" | "already_member" | "already_invited" | "invalid_email";
  invitationId?: number;
  message?: string;
}

export interface BatchInvitationResponse {
  results: BatchInvitationItemResult[];
  sentCount: number;
  failedCount: number;
}

export interface InvitationDto {
  id: number;
  workspaceId: number;
  workspaceName: string;
  workspaceSlug: string;
  token: string;
  type: "EMAIL" | "SHAREABLE_LINK";
  email?: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED" | "INACTIVE";
  expiresAt: string;
  maxUses?: number;
  usesCount: number;
  inviterName?: string;
  customMessage?: string;
  initialChannelIds?: number[];
  initialChannels?: ChannelDto[];
  isActive?: boolean;
  createdAt?: string;
}

export interface BatchAddChannelMembersResponse {
  addedCount: number;
  addedUserIds: number[];
  addedMembers: WorkspaceMemberDto[];
}

interface WorkspaceContextType {
  workspaces: WorkspaceDto[];
  currentWorkspace: WorkspaceDto | null;
  channels: ChannelDto[];
  currentChannel: ChannelDto | null;
  directMessages: DirectMessageDto[];
  currentDirectMessage: DirectMessageDto | null;
  members: WorkspaceMemberDto[];
  presenceMap: Record<number, "ONLINE" | "OFFLINE">;
  typingMap: Record<string, string>; // key: channel_{id} or dm_{targetUserId} -> username
  isLoading: boolean;
  createWorkspace: (name: string, slug?: string) => Promise<WorkspaceDto>;
  switchWorkspace: (workspaceSlug: string) => Promise<void>;
  createChannel: (
    name: string,
    type?: "PUBLIC" | "PRIVATE",
    description?: string
  ) => Promise<ChannelDto>;
  switchChannel: (channelId: number) => void;
  openDirectMessage: (targetUserId: number) => Promise<DirectMessageDto>;
  switchDirectMessage: (targetUserId: number) => void;
  getChannelMembers: (channelId: number) => Promise<WorkspaceMemberDto[]>;
  addChannelMember: (channelId: number, targetUserId: number) => Promise<void>;
  batchAddChannelMembers: (
    channelId: number,
    userIds: number[]
  ) => Promise<BatchAddChannelMembersResponse>;
  removeChannelMember: (
    channelId: number,
    targetUserId: number
  ) => Promise<void>;
  leaveChannel: (channelId: number) => Promise<void>;
  createEmailInvite: (
    email: string,
    role?: "MEMBER" | "ADMIN"
  ) => Promise<InvitationDto>;
  createEmailInvitation: (
    email: string,
    role?: "MEMBER" | "ADMIN"
  ) => Promise<InvitationDto>;
  createBatchInvites: (
    emails: string[],
    role?: "MEMBER" | "ADMIN",
    customMessage?: string,
    initialChannelIds?: number[]
  ) => Promise<BatchInvitationResponse>;
  createJoinLink: (
    maxUses?: number,
    expiresInHours?: number
  ) => Promise<InvitationDto>;
  getShareableJoinLink: () => Promise<InvitationDto>;
  toggleShareableJoinLink: () => Promise<InvitationDto>;
  resetShareableJoinLink: () => Promise<InvitationDto>;
  getPendingInvitations: () => Promise<InvitationDto[]>;
  resendInvitation: (id: number) => Promise<InvitationDto>;
  revokeInvitation: (id: number) => Promise<void>;
  acceptInvite: (token: string) => Promise<WorkspaceDto>;
  acceptInvitation: (token: string) => Promise<WorkspaceDto>;
  refreshWorkspaces: () => Promise<void>;
  refreshChannels: () => Promise<void>;
  refreshDirectMessages: () => Promise<void>;
  refreshMembers: () => Promise<void>;
}

const WorkspaceContext = createContext<WorkspaceContextType | null>(null);

export const WorkspaceProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { isAuthenticated, userId, username } = useAuth();
  const { connected, subscribe, stompClientRef } = useConnection();

  const [workspaces, setWorkspaces] = useState<WorkspaceDto[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<WorkspaceDto | null>(
    null
  );
  const [channels, setChannels] = useState<ChannelDto[]>([]);
  const [currentChannel, setCurrentChannel] = useState<ChannelDto | null>(null);
  const [directMessages, setDirectMessages] = useState<DirectMessageDto[]>([]);
  const [currentDirectMessage, setCurrentDirectMessage] =
    useState<DirectMessageDto | null>(null);
  const [members, setMembers] = useState<WorkspaceMemberDto[]>([]);
  const [presenceMap, setPresenceMap] = useState<
    Record<number, "ONLINE" | "OFFLINE">
  >({});
  const [typingMap, setTypingMap] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);

  const currentWorkspaceIdRef = useRef<number | null>(null);
  currentWorkspaceIdRef.current = currentWorkspace?.id || null;

  const refreshWorkspaces = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const data: WorkspaceDto[] = await apiFetch("/api/workspaces/me");
      setWorkspaces(data || []);
      if (data && data.length > 0 && !currentWorkspaceIdRef.current) {
        setCurrentWorkspace(data[0]);
      }
    } catch (err) {
      console.warn("Failed to fetch workspaces:", err);
    }
  }, [isAuthenticated]);

  const refreshChannels = useCallback(async () => {
    if (!currentWorkspace) return;
    try {
      const data: ChannelDto[] = await apiFetch(
        `/api/workspaces/${currentWorkspace.id}/channels`
      );
      const list = data || [];
      setChannels(list);
      setCurrentChannel((prev) => {
        if (!prev) {
          if (!currentDirectMessage && list.length > 0) {
            return list.find((c) => c.slug === "general") || list[0];
          }
          return null;
        }
        const updated = list.find((c) => c.id === prev.id);
        return (
          updated || list.find((c) => c.slug === "general") || list[0] || null
        );
      });
    } catch (err) {
      console.warn("Failed to fetch channels:", err);
    }
  }, [currentWorkspace, currentDirectMessage]);

  const refreshDirectMessages = useCallback(async () => {
    if (!currentWorkspace) return;
    try {
      const data: DirectMessageDto[] = await apiFetch(
        `/api/workspaces/${currentWorkspace.id}/dm`
      );
      setDirectMessages(data || []);
      if (Array.isArray(data)) {
        setPresenceMap((prev) => {
          const next = { ...prev };
          data.forEach((dm) => {
            if (dm.targetMember?.userId && dm.targetMember?.presenceStatus) {
              next[dm.targetMember.userId] = dm.targetMember.presenceStatus;
            }
          });
          return next;
        });
      }
    } catch (err) {
      console.warn("Failed to fetch direct messages:", err);
    }
  }, [currentWorkspace]);

  const refreshMembers = useCallback(async () => {
    if (!currentWorkspace) return;
    try {
      const data: WorkspaceMemberDto[] = await apiFetch(
        `/api/workspaces/${currentWorkspace.id}/members`
      );
      setMembers(data || []);
      if (Array.isArray(data)) {
        setPresenceMap((prev) => {
          const next = { ...prev };
          data.forEach((m) => {
            if (m.userId && m.presenceStatus) {
              next[m.userId] = m.presenceStatus;
            }
          });
          return next;
        });
      }
    } catch (err) {
      console.warn("Failed to fetch members:", err);
    }
  }, [currentWorkspace]);

  useEffect(() => {
    if (isAuthenticated) {
      refreshWorkspaces();
    } else {
      setWorkspaces([]);
      setCurrentWorkspace(null);
      setChannels([]);
      setCurrentChannel(null);
      setDirectMessages([]);
      setCurrentDirectMessage(null);
      setMembers([]);
      setPresenceMap({});
    }
  }, [isAuthenticated, refreshWorkspaces]);

  useEffect(() => {
    if (currentWorkspace) {
      refreshChannels();
      refreshDirectMessages();
      refreshMembers();
    }
  }, [
    currentWorkspace,
    refreshChannels,
    refreshDirectMessages,
    refreshMembers,
  ]);

  // STOMP Subscriptions for active Workspace
  useEffect(() => {
    if (!connected || !currentWorkspace || !isAuthenticated) return;

    const wId = currentWorkspace.id;

    // Fetch initially online users from REST API to hydrate presence immediately
    apiFetch("/api/v1/users/online")
      .then((onlineList: unknown) => {
        if (Array.isArray(onlineList)) {
          setPresenceMap((prev) => {
            const next = { ...prev };
            (
              onlineList as {
                userId: string | number;
                status: string;
              }[]
            ).forEach((u) => {
              if (u.userId) {
                const numId = Number(u.userId);
                next[numId] =
                  u.status?.toUpperCase() === "ONLINE" ? "ONLINE" : "OFFLINE";
              }
            });
            return next;
          });
        }
      })
      .catch((err) => {
        console.warn("Failed to fetch online users for workspace:", err);
      });

    // 1. Workspace Presence Topic
    const unsubPresence = subscribe(
      `/topic/workspace.${wId}.presence`,
      (frame) => {
        try {
          const payload = JSON.parse(frame.body);
          if (payload.userId && payload.status) {
            setPresenceMap((prev) => ({
              ...prev,
              [Number(payload.userId)]:
                payload.status?.toUpperCase() === "ONLINE"
                  ? "ONLINE"
                  : "OFFLINE",
            }));
          }
        } catch {}
      }
    );

    // 2. Personal User Online Queue (for real-time connect/disconnect events)
    const unsubUserOnline = subscribe("/user/queue/online", (frame) => {
      try {
        const payload = JSON.parse(frame.body);
        if (payload.userId && payload.status) {
          setPresenceMap((prev) => ({
            ...prev,
            [Number(payload.userId)]:
              payload.status?.toUpperCase() === "ONLINE" ? "ONLINE" : "OFFLINE",
          }));
        }
      } catch {}
    });

    // 3. Workspace Events (e.g. member joined, channel created)
    const unsubEvents = subscribe(`/topic/workspace.${wId}.events`, (frame) => {
      try {
        const payload = JSON.parse(frame.body);
        if (payload.type === "MEMBER_JOINED") {
          refreshMembers();
          refreshWorkspaces();
        } else if (
          payload.type === "CHANNEL_CREATED" ||
          payload.type === "CHANNEL_MEMBER_ADDED" ||
          payload.type === "CHANNEL_UPDATED"
        ) {
          refreshChannels();
        } else if (payload.type === "CHANNEL_MEMBER_REMOVED") {
          refreshChannels();
          if (
            payload.userId === userId &&
            currentChannel?.id === payload.channelId
          ) {
            setChannels((prevChannels) => {
              const fallback =
                prevChannels.find(
                  (c) => c.slug === "general" && c.id !== payload.channelId
                ) || null;
              setCurrentChannel(fallback);
              return prevChannels;
            });
            if (
              payload.removedByUsername &&
              payload.removedByUsername !== username
            ) {
              if (typeof window !== "undefined") {
                window.dispatchEvent(
                  new CustomEvent("toast-notification", {
                    detail: {
                      title: "Channel Access Removed",
                      message: `You were removed from this channel by @${payload.removedByUsername}`,
                      type: "info",
                    },
                  })
                );
              }
            }
          }
        }
      } catch {}
    });

    // 4. User DMs Queue
    const unsubDMs = subscribe(
      `/user/queue/workspace.${wId}.messages`,
      (frame) => {
        try {
          const msg = JSON.parse(frame.body);
          refreshDirectMessages();
          if (typeof window !== "undefined") {
            window.dispatchEvent(
              new CustomEvent("workspace:dm:received", { detail: msg })
            );
          }
        } catch {}
      }
    );

    // 5. Inbound DM Typing
    const unsubDMTyping = subscribe(
      `/user/queue/workspace.${wId}.typing`,
      (frame) => {
        try {
          const payload = JSON.parse(frame.body);
          if (payload.senderId && payload.senderUsername) {
            const key = `dm_${payload.senderId}`;
            if (payload.isTyping) {
              setTypingMap((prev) => ({
                ...prev,
                [key]: payload.senderUsername,
              }));
            } else {
              setTypingMap((prev) => {
                const next = { ...prev };
                delete next[key];
                return next;
              });
            }
          }
        } catch {}
      }
    );

    // Broadcast our presence in the workspace
    if (stompClientRef.current && connected) {
      stompClientRef.current.publish({
        destination: `/app/workspace.${wId}.presence`,
        body: JSON.stringify({ status: "ONLINE" }),
      });
      stompClientRef.current.publish({
        destination: "/app/noti.status",
        body: JSON.stringify({ status: "ONLINE" }),
      });
    }

    return () => {
      unsubPresence();
      unsubUserOnline();
      unsubEvents();
      unsubDMs();
      unsubDMTyping();
    };
  }, [
    connected,
    currentWorkspace,
    isAuthenticated,
    subscribe,
    stompClientRef,
    refreshMembers,
    refreshWorkspaces,
    refreshDirectMessages,
    refreshChannels,
  ]);

  const createWorkspace = async (
    name: string,
    slug?: string
  ): Promise<WorkspaceDto> => {
    setIsLoading(true);
    try {
      const res: WorkspaceDto = await apiFetch("/api/workspaces", {
        method: "POST",
        body: JSON.stringify({ name, slug }),
      });
      setWorkspaces((prev) => [...prev, res]);
      setCurrentWorkspace(res);
      setCurrentChannel(null);
      setCurrentDirectMessage(null);
      return res;
    } finally {
      setIsLoading(false);
    }
  };

  const switchWorkspace = async (workspaceSlug: string) => {
    const ws = workspaces.find((w) => w.slug === workspaceSlug);
    if (ws) {
      setCurrentWorkspace(ws);
      setCurrentChannel(null);
      setCurrentDirectMessage(null);
    } else {
      try {
        const fetched: WorkspaceDto = await apiFetch(
          `/api/workspaces/by-slug/${workspaceSlug}`
        );
        setCurrentWorkspace(fetched);
        setCurrentChannel(null);
        setCurrentDirectMessage(null);
      } catch (err) {
        console.error("Failed to switch workspace:", err);
      }
    }
  };

  const createChannel = async (
    name: string,
    type: "PUBLIC" | "PRIVATE" = "PUBLIC",
    description?: string
  ): Promise<ChannelDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    const res: ChannelDto = await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/channels`,
      {
        method: "POST",
        body: JSON.stringify({ name, type, description }),
      }
    );
    setChannels((prev) => [...prev, res]);
    setCurrentChannel(res);
    setCurrentDirectMessage(null);
    return res;
  };

  const switchChannel = (channelId: number) => {
    const ch = channels.find((c) => c.id === channelId);
    if (ch) {
      setCurrentChannel(ch);
      setCurrentDirectMessage(null);
    }
  };

  const openDirectMessage = async (
    targetUserId: number
  ): Promise<DirectMessageDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    const res: DirectMessageDto = await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/dm/${targetUserId}`,
      {
        method: "POST",
      }
    );
    setDirectMessages((prev) => {
      const exists = prev.some(
        (dm) => dm.conversationId === res.conversationId
      );
      return exists ? prev : [res, ...prev];
    });
    setCurrentDirectMessage(res);
    setCurrentChannel(null);
    return res;
  };

  const switchDirectMessage = (targetUserId: number) => {
    const dm = directMessages.find(
      (d) => d.targetMember.userId === targetUserId
    );
    if (dm) {
      setCurrentDirectMessage(dm);
      setCurrentChannel(null);
    } else {
      openDirectMessage(targetUserId);
    }
  };

  const getChannelMembers = async (
    channelId: number
  ): Promise<WorkspaceMemberDto[]> => {
    return await apiFetch(`/api/channels/${channelId}/members`);
  };

  const addChannelMember = async (channelId: number, targetUserId: number) => {
    await apiFetch(`/api/channels/${channelId}/members`, {
      method: "POST",
      body: JSON.stringify({ userId: targetUserId }),
    });
    refreshChannels();
  };

  const batchAddChannelMembers = async (
    channelId: number,
    userIds: number[]
  ): Promise<BatchAddChannelMembersResponse> => {
    const res: BatchAddChannelMembersResponse = await apiFetch(
      `/api/channels/${channelId}/members/batch`,
      {
        method: "POST",
        body: JSON.stringify({ userIds }),
      }
    );
    refreshChannels();
    return res;
  };

  const removeChannelMember = async (
    channelId: number,
    targetUserId: number
  ) => {
    await apiFetch(`/api/channels/${channelId}/members/${targetUserId}`, {
      method: "DELETE",
    });
    refreshChannels();
  };

  const leaveChannel = async (channelId: number) => {
    if (!userId) return;
    await apiFetch(`/api/channels/${channelId}/members/${userId}`, {
      method: "DELETE",
    });
    refreshChannels();
  };

  const createEmailInvite = async (
    email: string,
    role: "MEMBER" | "ADMIN" = "MEMBER"
  ): Promise<InvitationDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/invitations`,
      {
        method: "POST",
        body: JSON.stringify({ email, role }),
      }
    );
  };

  const createBatchInvites = async (
    emails: string[],
    role: "MEMBER" | "ADMIN" = "MEMBER",
    customMessage?: string,
    initialChannelIds?: number[]
  ): Promise<BatchInvitationResponse> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/invitations/batch`,
      {
        method: "POST",
        body: JSON.stringify({
          emails,
          role,
          customMessage,
          initialChannelIds,
        }),
      }
    );
  };

  const getShareableJoinLink = async (): Promise<InvitationDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(`/api/workspaces/${currentWorkspace.id}/join-link`);
  };

  const toggleShareableJoinLink = async (): Promise<InvitationDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/join-link/toggle`,
      {
        method: "POST",
      }
    );
  };

  const resetShareableJoinLink = async (): Promise<InvitationDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/join-link/reset`,
      {
        method: "POST",
      }
    );
  };

  const getPendingInvitations = async (): Promise<InvitationDto[]> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(
      `/api/workspaces/${currentWorkspace.id}/invitations/pending`
    );
  };

  const resendInvitation = async (id: number): Promise<InvitationDto> => {
    return await apiFetch(`/api/invitations/${id}/resend`, {
      method: "POST",
    });
  };

  const revokeInvitation = async (id: number): Promise<void> => {
    await apiFetch(`/api/invitations/${id}`, {
      method: "DELETE",
    });
  };

  const createJoinLink = async (
    maxUses?: number,
    expiresInHours?: number
  ): Promise<InvitationDto> => {
    if (!currentWorkspace) throw new Error("No active workspace");
    return await apiFetch(`/api/workspaces/${currentWorkspace.id}/join-link`, {
      method: "POST",
      body: JSON.stringify({ maxUses, expiresInHours }),
    });
  };

  const acceptInvite = async (token: string): Promise<WorkspaceDto> => {
    const res: WorkspaceDto = await apiFetch(
      `/api/invitations/${token}/accept`,
      {
        method: "POST",
      }
    );
    await refreshWorkspaces();
    setCurrentWorkspace(res);
    return res;
  };

  return (
    <WorkspaceContext.Provider
      value={{
        workspaces,
        currentWorkspace,
        channels,
        currentChannel,
        directMessages,
        currentDirectMessage,
        members,
        presenceMap,
        typingMap,
        isLoading,
        createWorkspace,
        switchWorkspace,
        createChannel,
        switchChannel,
        openDirectMessage,
        switchDirectMessage,
        getChannelMembers,
        addChannelMember,
        batchAddChannelMembers,
        removeChannelMember,
        leaveChannel,
        createEmailInvite,

        createEmailInvitation: createEmailInvite,
        createBatchInvites,
        createJoinLink,
        getShareableJoinLink,
        toggleShareableJoinLink,
        resetShareableJoinLink,
        getPendingInvitations,
        resendInvitation,
        revokeInvitation,
        acceptInvite,
        acceptInvitation: acceptInvite,
        refreshWorkspaces,
        refreshChannels,
        refreshDirectMessages,
        refreshMembers,
      }}
    >
      {children}
    </WorkspaceContext.Provider>
  );
};

export const useWorkspace = () => {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error("useWorkspace must be used within a WorkspaceProvider");
  }
  return context;
};
