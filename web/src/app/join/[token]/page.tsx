"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace, type InvitationDto } from "@/context/WorkspaceContext";
import { apiFetch } from "@/lib/api";
import {
  saveAuthRedirect,
  AUTH_REDIRECT_STORAGE_KEY,
} from "@/lib/authRedirect";

export default function JoinPage() {
  const params = useParams();
  const router = useRouter();
  const token = params?.token as string;

  const { isAuthenticated } = useAuth();
  const { acceptInvitation } = useWorkspace();

  const [invitation, setInvitation] = useState<InvitationDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    saveAuthRedirect(`/join/${token}`);

    const fetchInvite = async () => {
      try {
        const data: InvitationDto = await apiFetch(`/api/invitations/${token}`);
        setInvitation(data);
      } catch (err: any) {
        setError(err?.message || "Invalid or expired invitation link.");
      } finally {
        setLoading(false);
      }
    };
    fetchInvite();
  }, [token]);

  const handleAccept = async () => {
    if (!token) return;
    setJoining(true);
    setError(null);
    try {
      const joinedWorkspace = await acceptInvitation(token);
      if (typeof window !== "undefined") {
        sessionStorage.removeItem(AUTH_REDIRECT_STORAGE_KEY);
      }
      router.push(`/w/${joinedWorkspace.slug}`);
    } catch (err: any) {
      setError(err?.message || "Failed to join workspace.");
      setJoining(false);
    }
  };

  const handleNavigateAuth = (mode: "login" | "signup" = "login") => {
    saveAuthRedirect(`/join/${token}`);
    const query =
      mode === "signup"
        ? `/?redirect=/join/${token}&mode=signup`
        : `/?redirect=/join/${token}`;
    router.push(query);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="animate-pulse flex flex-col items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-surface-container" />
          <div className="h-4 w-32 bg-surface-container rounded" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-md w-full p-8 text-center flex flex-col items-center">
        {/* Workspace Avatar */}
        <div className="w-16 h-16 rounded-3xl bg-primary/10 border border-primary/20 flex items-center justify-center text-2xl font-black text-primary mb-4 shadow-sm">
          {invitation?.workspaceName?.charAt(0).toUpperCase() ||
            (error ? "!" : "W")}
        </div>

        {error && !invitation ? (
          <div className="w-full">
            <h1 className="text-xl font-black text-on-surface mb-2">
              Invitation Unavailable
            </h1>
            <div className="w-full mb-6 p-4 rounded-2xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
            <button
              type="button"
              onClick={() => router.push("/")}
              className="w-full py-3 px-4 bg-surface-container hover:bg-surface-container-high text-on-surface font-bold text-sm rounded-2xl transition-all border border-outline-variant/30"
            >
              Return to Home
            </button>
          </div>
        ) : (
          <>
            <h1 className="text-xl font-black text-on-surface mb-1">
              Join {invitation?.workspaceName || "Workspace"}
            </h1>
            <p className="text-xs text-outline mb-4">
              {invitation?.inviterName ? (
                <>
                  <strong className="text-on-surface">
                    @{invitation.inviterName}
                  </strong>{" "}
                  invited you to collaborate as a{" "}
                </>
              ) : (
                "You have been invited to join this workspace as a "
              )}
              <strong className="text-on-surface capitalize">
                {invitation?.role.toLowerCase() || "member"}
              </strong>
              .
            </p>

            {/* Custom Welcome Message */}
            {invitation?.customMessage && (
              <div className="w-full mb-4 p-3.5 rounded-2xl bg-surface-container-low border border-outline-variant/15 text-left">
                <span className="text-[10px] font-bold text-outline uppercase tracking-wider block mb-1">
                  Note from @{invitation.inviterName || "Inviter"}
                </span>
                <p className="text-xs text-on-surface italic leading-relaxed">
                  &ldquo;{invitation.customMessage}&rdquo;
                </p>
              </div>
            )}

            {/* Initial Assigned Channels */}
            {invitation?.initialChannels &&
              invitation.initialChannels.length > 0 && (
                <div className="w-full mb-5 p-3 rounded-2xl bg-surface-container/50 border border-outline-variant/15 text-left">
                  <span className="text-[10px] font-bold text-outline uppercase tracking-wider block mb-1.5">
                    Channels You&apos;ll Join
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {invitation.initialChannels.map((ch) => (
                      <span
                        key={ch.id}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl bg-surface-container-high text-[11px] font-semibold text-on-surface"
                      >
                        <span className="text-outline">
                          {ch.type === "PRIVATE" ? "🔒" : "#"}
                        </span>
                        {ch.name}
                      </span>
                    ))}
                  </div>
                </div>
              )}

            {error && (
              <div className="w-full mb-4 p-3 rounded-2xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
                {error}
              </div>
            )}

            {isAuthenticated ? (
              <button
                type="button"
                onClick={handleAccept}
                disabled={joining || !invitation}
                className="w-full py-3 px-4 bg-primary text-white font-bold text-sm rounded-2xl hover:bg-primary/90 transition-all shadow-md shadow-primary/20 disabled:opacity-50"
              >
                {joining
                  ? "Joining..."
                  : `Accept & Join ${invitation?.workspaceName || ""}`.trim()}
              </button>
            ) : (
              <div className="w-full space-y-3">
                <button
                  type="button"
                  onClick={() => handleNavigateAuth("login")}
                  className="w-full py-3 px-4 bg-primary text-white font-bold text-sm rounded-2xl hover:bg-primary/90 transition-all shadow-md shadow-primary/20"
                >
                  Sign In to Accept
                </button>
                <button
                  type="button"
                  onClick={() => handleNavigateAuth("signup")}
                  className="w-full py-3 px-4 bg-surface-container hover:bg-surface-container-high text-on-surface font-bold text-sm rounded-2xl transition-all border border-outline-variant/30"
                >
                  Create Account to Join
                </button>
                <p className="text-[11px] text-outline">
                  Sign in or create a new account to accept this invitation.
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
