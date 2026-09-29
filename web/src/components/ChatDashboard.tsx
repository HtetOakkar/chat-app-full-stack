"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import { apiFetch } from "@/lib/api";
import WorkspaceRail from "./WorkspaceRail";
import WorkspaceSidebar from "./WorkspaceSidebar";
import WorkspaceChannelViewport from "./WorkspaceChannelViewport";
import WorkspaceDMViewport from "./WorkspaceDMViewport";
import WorkspaceOnboardingModal from "./WorkspaceOnboardingModal";
import CreateChannelModal from "./CreateChannelModal";
import InviteMembersModal from "./InviteMembersModal";
import MemberDirectoryModal from "./MemberDirectoryModal";
import ChannelRemovalNotice from "./ChannelRemovalNotice";
import type { ActiveChat } from "./Sidebar";
import ChatViewport from "./ChatViewport";
import MyProfileCard from "./MyProfileCard";
import UserProfileCard from "./UserProfileCard";
import SettingsPage from "./SettingsPage";
import IncomingCallModal from "./IncomingCallModal";
import ActiveCallOverlay from "./ActiveCallOverlay";

export default function ChatDashboard() {
  const { logout, username, userId } = useAuth();
  const {
    workspaces,
    currentWorkspace,
    currentChannel,
    currentDirectMessage,
    clearActiveConversation,
    isLoading: workspaceLoading,
  } = useWorkspace();

  const [activeChat, setActiveChat] = useState<ActiveChat | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [profile, setProfile] = useState<{ fullName?: string | null } | null>(
    null
  );
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [viewMode, setViewMode] = useState<
    "chat" | "profile" | "user-profile" | "settings"
  >("chat");
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const [selectedProfileUser, setSelectedProfileUser] = useState<{
    id: number;
    username: string;
    status?: string;
  } | null>(null);

  // Modals
  const [showWorkspaceModal, setShowWorkspaceModal] = useState(false);
  const [showCreateChannel, setShowCreateChannel] = useState(false);
  const [showInvite, setShowInvite] = useState(false);
  const [showDirectory, setShowDirectory] = useState(false);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const data = await apiFetch("/api/v1/users/profile");
        if (data) {
          setProfile(data);
        }
      } catch {}
    };
    fetchProfile();
  }, []);

  const handleSelectChat = useCallback((chat: ActiveChat | null) => {
    setActiveChat(chat);
    setViewMode("chat");
  }, []);

  const handleViewUserProfile = useCallback(
    (user: { id: number; username: string; status?: string }) => {
      setSelectedProfileUser(user);
      setViewMode("user-profile");
    },
    []
  );

  const handleBannerAction = useCallback(() => {
    setRefreshTrigger((prev) => prev + 1);
  }, []);

  return (
    <div className="flex flex-col h-dvh overflow-hidden bg-background">
      {/* Global Top Bar */}
      <header className="shrink-0 bg-surface-container-lowest/80 backdrop-blur-sm border-b border-outline-variant/10 px-4 md:px-6 py-3 md:py-2.5 flex items-center justify-between z-20 relative">
        <div className="flex items-center gap-3">
          <span className="text-[10px] md:text-[9px] font-bold uppercase tracking-[0.15em] text-outline">
            {currentWorkspace ? currentWorkspace.name : "Workspaces"}
          </span>
        </div>
        <div className="flex items-center gap-2 md:gap-3">
          {username && (
            <div className="relative">
              <button
                type="button"
                onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                className="flex items-center gap-2 hover:bg-surface-container-high/60 p-1.5 rounded-lg transition-all border border-transparent hover:border-outline-variant/10"
              >
                <div className="w-8 h-8 md:w-7 md:h-7 rounded-lg bg-primary/10 flex items-center justify-center text-[11px] md:text-[10px] font-extrabold text-primary border border-primary/10">
                  {username.charAt(0).toUpperCase()}
                </div>
                <span className="text-[10px] font-bold text-on-surface hidden lg:block">
                  {profile?.fullName
                    ? `${profile.fullName} (${username})`
                    : username}
                </span>
                <span className="material-symbols-outlined text-[14px] text-outline ml-1">
                  expand_more
                </span>
              </button>

              {isUserMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 bg-surface-container-lowest border border-outline-variant/20 rounded-xl shadow-lg overflow-hidden flex flex-col z-50">
                  <button
                    type="button"
                    onClick={() => {
                      setViewMode("profile");
                      setIsUserMenuOpen(false);
                    }}
                    className="flex items-center gap-2 px-4 py-3 text-sm font-medium text-on-surface hover:bg-surface-container-low transition-colors text-left"
                  >
                    <span className="material-symbols-outlined text-base">
                      person
                    </span>
                    View Profile
                  </button>
                  <div className="h-px w-full bg-outline-variant/10" />
                  <button
                    type="button"
                    onClick={() => {
                      setViewMode("settings");
                      setIsUserMenuOpen(false);
                    }}
                    className="flex items-center gap-2 px-4 py-3 text-sm font-medium text-on-surface hover:bg-surface-container-low transition-colors text-left"
                  >
                    <span className="material-symbols-outlined text-base">
                      settings
                    </span>
                    Settings
                  </button>
                  <div className="h-px w-full bg-outline-variant/10" />
                  <button
                    type="button"
                    onClick={() => {
                      setShowLogoutConfirm(true);
                      setIsUserMenuOpen(false);
                    }}
                    className="flex items-center gap-2 px-4 py-3 text-sm font-medium text-error hover:bg-error-container/20 transition-colors text-left"
                  >
                    <span className="material-symbols-outlined text-base">
                      logout
                    </span>
                    Logout
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </header>

      {/* 3-Tier Multi-Tenant Architecture */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Tier 1: Workspace Rail */}
        <WorkspaceRail
          onOpenCreateWorkspace={() => setShowWorkspaceModal(true)}
        />

        {/* View content based on viewMode and workspace membership */}
        {viewMode === "profile" ? (
          <div className="flex-1 flex min-h-0">
            <MyProfileCard onBack={() => setViewMode("chat")} />
          </div>
        ) : viewMode === "settings" ? (
          <div className="flex-1 flex min-h-0">
            <SettingsPage onBack={() => setViewMode("chat")} />
          </div>
        ) : viewMode === "user-profile" && selectedProfileUser ? (
          <div className="flex-1 flex min-h-0">
            <UserProfileCard
              userId={selectedProfileUser.id}
              fallbackUsername={selectedProfileUser.username}
              currentUserId={userId}
              userStatus={selectedProfileUser.status}
              onBack={() => setViewMode("chat")}
              onSelectChat={handleSelectChat}
            />
          </div>
        ) : workspaces.length === 0 ? (
          /* Zero-Workspace Centered Empty State Canvas */
          <main
            aria-label="Dashboard Empty State"
            className="flex-1 flex flex-col items-center justify-center p-6 text-center select-none bg-background"
          >
            <div className="max-w-md w-full flex flex-col items-center">
              <div className="w-16 h-16 rounded-3xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary mb-5 shadow-sm">
                <span className="material-symbols-outlined text-3xl">
                  workspaces
                </span>
              </div>
              <h2 className="text-xl font-bold text-on-surface mb-2">
                No Workspaces Yet
              </h2>
              <p className="text-xs md:text-sm text-outline mb-6 leading-relaxed max-w-sm">
                You are not a member of any workspaces yet. Create a workspace
                to start collaborating in channels and direct messages.
              </p>
              <button
                type="button"
                onClick={() => setShowWorkspaceModal(true)}
                className="flex items-center gap-2 px-5 py-3 text-xs md:text-sm font-bold bg-primary text-white rounded-2xl hover:bg-primary/90 transition-all shadow-md shadow-primary/25 hover:scale-[1.02] active:scale-[0.98]"
              >
                <span className="material-symbols-outlined text-lg">add</span>
                Create Workspace
              </button>
            </div>
          </main>
        ) : (
          /* Workspaces Active: Tier 2 Sidebar & Tier 3 Content */
          <>
            <div
              className={`h-full ${
                (currentDirectMessage !== null ||
                  currentChannel !== null ||
                  activeChat !== null) &&
                viewMode === "chat"
                  ? "hidden md:flex"
                  : "flex w-full md:w-auto"
              }`}
            >
              <WorkspaceSidebar
                onOpenCreateChannel={() => setShowCreateChannel(true)}
                onOpenInvite={() => setShowInvite(true)}
                onOpenMemberDirectory={() => setShowDirectory(true)}
                onOpenSettings={() => setViewMode("settings")}
                onOpenProfile={() => setViewMode("profile")}
                onOpenCreateWorkspace={() => setShowWorkspaceModal(true)}
              />
            </div>

            <div
              className={`flex-1 flex flex-col min-w-0 ${
                activeChat === null &&
                currentDirectMessage === null &&
                currentChannel === null &&
                viewMode === "chat"
                  ? "hidden md:flex"
                  : "flex"
              }`}
            >
              <div className="flex-1 flex min-h-0">
                {currentDirectMessage ? (
                  <WorkspaceDMViewport
                    dm={currentDirectMessage}
                    onBack={clearActiveConversation}
                  />
                ) : currentChannel ? (
                  <WorkspaceChannelViewport
                    channel={currentChannel}
                    onBack={clearActiveConversation}
                  />
                ) : activeChat ? (
                  <ChatViewport
                    activeChat={activeChat}
                    onBannerAction={handleBannerAction}
                    onBackToList={() => handleSelectChat(null)}
                    onViewUserProfile={handleViewUserProfile}
                  />
                ) : (
                  <div className="hidden md:flex flex-1 flex-col items-center justify-center p-8 text-center bg-surface-container-lowest/30 select-none">
                    <div className="w-16 h-16 rounded-3xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary mb-4 shadow-xs">
                      <span className="material-symbols-outlined text-3xl">
                        chat
                      </span>
                    </div>
                    <h3 className="text-base font-bold text-on-surface mb-1">
                      No Conversation Selected
                    </h3>
                    <p className="text-xs text-outline max-w-xs leading-relaxed">
                      Select a channel or direct message from the sidebar to
                      start collaborating.
                    </p>
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>

      {/* Shown over any view, so a removed Member learns who removed them wherever they are */}
      <ChannelRemovalNotice />

      {/* Modals */}
      <WorkspaceOnboardingModal
        isOpen={showWorkspaceModal}
        onClose={() => setShowWorkspaceModal(false)}
      />
      <CreateChannelModal
        isOpen={showCreateChannel}
        onClose={() => setShowCreateChannel(false)}
      />
      <InviteMembersModal
        isOpen={showInvite}
        onClose={() => setShowInvite(false)}
      />
      <MemberDirectoryModal
        isOpen={showDirectory}
        onClose={() => setShowDirectory(false)}
        onOpenInvite={() => {
          setShowDirectory(false);
          setShowInvite(true);
        }}
      />

      {/* Logout Confirmation Overlay */}
      {showLogoutConfirm && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm flex items-center justify-center z-[100] p-4">
          <div className="bg-surface-container-lowest border border-outline-variant/30 rounded-3xl shadow-2xl max-w-sm w-full p-6">
            <h3 className="font-bold text-sm text-on-surface mb-2">Sign Out</h3>
            <p className="text-xs text-outline mb-6">
              Are you sure you want to sign out of your account?
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowLogoutConfirm(false)}
                className="px-4 py-2 text-xs font-bold text-on-surface hover:bg-surface-container-high rounded-xl transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={logout}
                className="px-4 py-2 text-xs font-bold bg-error text-white rounded-xl hover:bg-error/90 transition-colors shadow-sm"
              >
                Sign Out
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Call UI Components */}
      <IncomingCallModal />
      <ActiveCallOverlay />
    </div>
  );
}
