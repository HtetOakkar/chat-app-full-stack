"use client";

import { useState, useCallback, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { apiFetch } from "@/lib/api";
import Sidebar, { type ActiveChat } from "./Sidebar";
import ChatViewport from "./ChatViewport";
import MyProfileCard from "./MyProfileCard";

import UserProfileCard from "./UserProfileCard";

export default function ChatDashboard() {
  const { logout, username, userId } = useAuth();
  const [activeChat, setActiveChat] = useState<ActiveChat | null>({
    id: 0,
    username: "Global Registry Chat",
    isPublic: true,
  });
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [profile, setProfile] = useState<{ fullName?: string | null } | null>(
    null
  );
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [viewMode, setViewMode] = useState<"chat" | "profile" | "user-profile">(
    "chat"
  );
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const [selectedProfileUser, setSelectedProfileUser] = useState<{
    id: number;
    username: string;
    status?: string;
  } | null>(null);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const data = await apiFetch("/api/v1/users/profile");
        if (data) {
          setProfile(data);
        }
      } catch {
        // fail silently
      }
    };
    fetchProfile();
  }, []);

  useEffect(() => {
    const handleChatDeleted = (event: Event) => {
      const customEvent = event as CustomEvent;
      const { contactUserId } = customEvent.detail;
      setActiveChat((prev) => {
        if (prev && !prev.isPublic && prev.id === contactUserId) {
          return {
            id: 0,
            username: "Global Registry Chat",
            isPublic: true,
          };
        }
        return prev;
      });
      setRefreshTrigger((prev) => prev + 1);
    };

    window.addEventListener("chat:deleted", handleChatDeleted);
    return () => {
      window.removeEventListener("chat:deleted", handleChatDeleted);
    };
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
    // After accept/ignore/block, refresh sidebar data and clear pending status
    setRefreshTrigger((prev) => prev + 1);
    setActiveChat((prev) => {
      if (
        prev &&
        (prev.status === "PENDING_REQUEST" || prev.status === "NEGLECTED")
      ) {
        return { ...prev, status: undefined };
      }
      return prev;
    });
  }, []);

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background">
      {/* Global Top Bar */}
      <header className="shrink-0 bg-surface-container-lowest/80 backdrop-blur-sm border-b border-outline-variant/10 px-4 md:px-6 py-3 md:py-2.5 flex items-center justify-between z-20 relative">
        <div className="flex items-center gap-3">
          <span className="text-[10px] md:text-[9px] font-bold uppercase tracking-[0.15em] text-outline">
            Curator Dashboard
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
                <div className="absolute right-0 mt-2 w-48 bg-surface-container-lowest border border-outline-variant/20 rounded-xl shadow-lg overflow-hidden flex flex-col z-50">
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

      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Sidebar */}
        <Sidebar
          activeChat={activeChat}
          onSelectChat={handleSelectChat}
          onSelectProfileUser={handleViewUserProfile}
          refreshTrigger={refreshTrigger}
          viewMode={viewMode}
        />

        {/* Main Content */}
        <div
          className={`flex-1 flex flex-col min-w-0 ${
            activeChat === null && viewMode === "chat"
              ? "hidden md:flex"
              : "flex"
          }`}
        >
          {/* Main Viewport Content */}
          <div className="flex-1 flex min-h-0">
            {viewMode === "profile" ? (
              <MyProfileCard onBack={() => setViewMode("chat")} />
            ) : viewMode === "user-profile" && selectedProfileUser ? (
              <UserProfileCard
                userId={selectedProfileUser.id}
                fallbackUsername={selectedProfileUser.username}
                currentUserId={userId}
                userStatus={selectedProfileUser.status}
                onBack={() => setViewMode("chat")}
                onSelectChat={handleSelectChat}
              />
            ) : (
              <ChatViewport
                activeChat={activeChat}
                onBannerAction={handleBannerAction}
                onBackToList={() => handleSelectChat(null)}
                onViewUserProfile={handleViewUserProfile}
              />
            )}
          </div>
        </div>
      </div>

      {/* Logout Confirmation Overlay */}
      {showLogoutConfirm && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm flex items-center justify-center z-[100] p-4 animate-[fadeIn_0.2s_ease-out]">
          <div className="bg-surface-container-lowest outline outline-1 outline-outline-variant/30 rounded-2xl shadow-[0_24px_64px_rgba(0,66,117,0.12)] max-w-sm w-full overflow-hidden flex flex-col">
            <div className="px-6 py-5 border-b border-outline-variant/10 bg-surface-container-low flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-error/10 flex items-center justify-center text-error border border-error/10">
                <span className="material-symbols-outlined text-lg">
                  logout
                </span>
              </div>
              <h3 className="font-bold text-sm text-on-surface">
                Sign Out Confirmation
              </h3>
            </div>
            <div className="p-6">
              <p className="text-sm text-outline mb-6">
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
        </div>
      )}
    </div>
  );
}
