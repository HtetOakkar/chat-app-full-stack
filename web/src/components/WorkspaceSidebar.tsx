"use client";

import React, { useState } from "react";
import {
  useWorkspace,
  type ChannelDto,
  type DirectMessageDto,
  type WorkspaceMemberDto,
} from "@/context/WorkspaceContext";
import { useAuth } from "@/context/AuthContext";

interface WorkspaceSidebarProps {
  onOpenCreateChannel: () => void;
  onOpenInvite: () => void;
  onOpenMemberDirectory: () => void;
  onOpenSettings: () => void;
  onOpenProfile: () => void;
  onOpenCreateWorkspace?: () => void;
}

export default function WorkspaceSidebar({
  onOpenCreateChannel,
  onOpenInvite,
  onOpenMemberDirectory,
  onOpenSettings,
  onOpenProfile,
  onOpenCreateWorkspace,
}: WorkspaceSidebarProps) {
  const { userId, username } = useAuth();
  const {
    workspaces,
    currentWorkspace,
    switchWorkspace,
    channels,
    currentChannel,
    switchChannel,
    directMessages,
    currentDirectMessage,
    switchDirectMessage,
    presenceMap,
  } = useWorkspace();

  const [isWorkspaceMenuOpen, setIsWorkspaceMenuOpen] = useState(false);

  if (!currentWorkspace) {
    return (
      <aside className="w-full md:w-64 lg:w-72 shrink-0 bg-surface-container-low/60 border-r border-outline-variant/15 flex flex-col p-4">
        <div className="animate-pulse space-y-3">
          <div className="h-6 bg-surface-container-high rounded w-3/4" />
          <div className="h-4 bg-surface-container-high rounded w-1/2" />
        </div>
      </aside>
    );
  }

  const isOwnerOrAdmin =
    currentWorkspace.role === "OWNER" || currentWorkspace.role === "ADMIN";

  return (
    <aside
      aria-label="Workspace Sidebar"
      className="w-full md:w-64 lg:w-72 shrink-0 bg-surface-container-low/60 border-r border-outline-variant/15 flex flex-col justify-between overflow-hidden select-none z-20 h-full"
    >
      {/* Workspace Header */}
      <div className="shrink-0 p-3 md:p-3.5 border-b border-outline-variant/15 relative">
        <button
          type="button"
          onClick={() => setIsWorkspaceMenuOpen(!isWorkspaceMenuOpen)}
          className="w-full flex items-center justify-between p-2 rounded-xl hover:bg-surface-container-high/60 transition-colors group min-h-[44px] md:min-h-0"
        >
          <div className="flex flex-col items-start min-w-0 pr-2">
            <span className="font-extrabold text-sm text-on-surface truncate w-full text-left">
              {currentWorkspace.name}
            </span>
            <span className="text-[11px] font-medium text-outline truncate flex items-center gap-1.5">
              <span>{currentWorkspace.memberCount} members</span>
              <span>•</span>
              <span className="capitalize">
                {currentWorkspace.role.toLowerCase()}
              </span>
            </span>
          </div>
          <span className="material-symbols-outlined text-outline group-hover:text-on-surface transition-colors">
            unfold_more
          </span>
        </button>

        {/* Workspace Quick Actions Dropdown */}
        {isWorkspaceMenuOpen && (
          <div className="absolute left-3 right-3 top-full mt-1 bg-surface-container-lowest border border-outline-variant/20 rounded-2xl shadow-xl z-50 p-1.5 flex flex-col gap-0.5 max-h-[80vh] overflow-y-auto">
            {/* Mobile Workspace Switcher Section */}
            {workspaces && workspaces.length > 0 && (
              <div className="md:hidden border-b border-outline-variant/15 pb-1.5 mb-1">
                <div className="px-3 py-1 text-[10px] font-bold text-outline uppercase tracking-wider">
                  Switch Workspace
                </div>
                <div className="max-h-44 overflow-y-auto space-y-0.5">
                  {workspaces.map((ws) => (
                    <button
                      key={ws.id}
                      type="button"
                      onClick={() => {
                        setIsWorkspaceMenuOpen(false);
                        switchWorkspace(ws.slug);
                      }}
                      className={`w-full flex items-center justify-between px-3 py-2.5 text-xs rounded-xl transition-colors ${
                        currentWorkspace?.id === ws.id
                          ? "bg-primary/10 text-primary font-bold"
                          : "text-on-surface hover:bg-surface-container-high/60"
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <div className="w-6 h-6 rounded-lg bg-surface-container flex items-center justify-center font-bold text-[10px]">
                          {ws.name.charAt(0).toUpperCase()}
                        </div>
                        <span className="truncate">{ws.name}</span>
                      </div>
                      {currentWorkspace?.id === ws.id && (
                        <span className="material-symbols-outlined text-sm text-primary">
                          check
                        </span>
                      )}
                    </button>
                  ))}
                </div>
                {onOpenCreateWorkspace && (
                  <button
                    type="button"
                    onClick={() => {
                      setIsWorkspaceMenuOpen(false);
                      onOpenCreateWorkspace();
                    }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-xs font-semibold text-primary hover:bg-primary/5 rounded-xl transition-colors text-left mt-0.5"
                  >
                    <span className="material-symbols-outlined text-base">
                      add
                    </span>
                    Create Workspace
                  </button>
                )}
              </div>
            )}

            <button
              type="button"
              onClick={() => {
                setIsWorkspaceMenuOpen(false);
                onOpenInvite();
              }}
              className="flex items-center gap-2.5 px-3 py-2.5 md:py-2 text-xs font-semibold text-primary hover:bg-primary/5 rounded-xl transition-colors text-left"
            >
              <span className="material-symbols-outlined text-base">
                person_add
              </span>
              Invite Colleagues
            </button>
            <button
              type="button"
              onClick={() => {
                setIsWorkspaceMenuOpen(false);
                onOpenMemberDirectory();
              }}
              className="flex items-center gap-2.5 px-3 py-2.5 md:py-2 text-xs font-semibold text-on-surface hover:bg-surface-container-high/60 rounded-xl transition-colors text-left"
            >
              <span className="material-symbols-outlined text-base">group</span>
              Member Directory
            </button>
          </div>
        )}
      </div>

      {/* Main Channels & DMs Scroll Area */}
      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-6">
        {/* CHANNELS SECTION */}
        <div>
          <div className="flex items-center justify-between px-2 mb-1.5">
            <span className="text-[11px] font-bold uppercase tracking-wider text-outline">
              Channels
            </span>
            <button
              type="button"
              onClick={onOpenCreateChannel}
              title="Create Channel"
              aria-label="Create Channel"
              className="w-8 h-8 md:w-5 md:h-5 rounded-lg flex items-center justify-center text-outline hover:text-primary hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-sm font-bold">
                add
              </span>
            </button>
          </div>

          <div className="space-y-0.5">
            {channels.map((ch: ChannelDto) => {
              const isActive =
                currentChannel?.id === ch.id && currentDirectMessage === null;
              return (
                <button
                  key={ch.id}
                  type="button"
                  onClick={() => switchChannel(ch.id)}
                  className={`w-full flex items-center gap-2 px-3 py-2.5 md:py-1.5 rounded-xl text-xs font-semibold transition-all min-h-[44px] md:min-h-0 ${
                    isActive
                      ? "bg-primary/10 text-primary font-bold shadow-xs"
                      : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/50"
                  }`}
                >
                  <span className="material-symbols-outlined text-base opacity-75">
                    {ch.type === "PRIVATE" ? "lock" : "tag"}
                  </span>
                  <span className="truncate">{ch.name}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* DIRECT MESSAGES SECTION */}
        <div>
          <div className="flex items-center justify-between px-2 mb-1.5">
            <span className="text-[11px] font-bold uppercase tracking-wider text-outline">
              Direct Messages
            </span>
            <button
              type="button"
              onClick={onOpenMemberDirectory}
              title="New Direct Message"
              aria-label="New Direct Message"
              className="w-8 h-8 md:w-6 md:h-6 rounded-lg flex items-center justify-center text-outline hover:text-primary hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-sm font-bold">
                add
              </span>
            </button>
          </div>

          <div className="space-y-0.5">
            {directMessages.map((dm: DirectMessageDto) => {
              const target = dm.targetMember;
              const isActive =
                currentDirectMessage?.conversationId === dm.conversationId;
              const isOnline = presenceMap[target.userId] === "ONLINE";

              return (
                <button
                  key={dm.conversationId}
                  type="button"
                  onClick={() => switchDirectMessage(target.userId)}
                  className={`w-full flex items-center justify-between px-3 py-2.5 md:py-1.5 rounded-xl text-xs font-semibold transition-all min-h-[44px] md:min-h-0 ${
                    isActive
                      ? "bg-primary/10 text-primary font-bold shadow-xs"
                      : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high/50"
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <div className="relative shrink-0">
                      <div className="w-5 h-5 rounded-full bg-primary/15 flex items-center justify-center text-[10px] font-bold text-primary">
                        {target.username.charAt(0).toUpperCase()}
                      </div>
                      <span
                        className={`absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full ring-2 ring-surface-container-lowest ${
                          isOnline ? "bg-emerald-500" : "bg-neutral-400"
                        }`}
                      />
                    </div>
                    <span className="truncate">
                      {target.fullName || target.username}
                    </span>
                  </div>

                  {dm.unreadCount && dm.unreadCount > 0 ? (
                    <span className="shrink-0 bg-primary text-white text-[10px] font-bold px-1.5 py-0.2 rounded-full">
                      {dm.unreadCount}
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* User Footer Card */}
      <div className="shrink-0 p-3 border-t border-outline-variant/15 bg-surface-container-lowest/40 flex items-center justify-between">
        <button
          type="button"
          onClick={onOpenProfile}
          className="flex items-center gap-2.5 hover:bg-surface-container-high/50 p-1.5 rounded-xl transition-colors min-w-0 text-left min-h-[44px]"
        >
          <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-xs font-bold text-primary shrink-0">
            {username ? username.charAt(0).toUpperCase() : "U"}
          </div>
          <div className="min-w-0">
            <span className="block text-xs font-bold text-on-surface truncate">
              {username}
            </span>
            <span className="block text-[10px] font-medium text-emerald-500">
              Online
            </span>
          </div>
        </button>

        <button
          type="button"
          onClick={onOpenSettings}
          title="Settings"
          aria-label="Settings"
          className="w-11 h-11 md:w-8 md:h-8 rounded-xl flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container-high transition-colors"
        >
          <span className="material-symbols-outlined text-lg">settings</span>
        </button>
      </div>
    </aside>
  );
}
