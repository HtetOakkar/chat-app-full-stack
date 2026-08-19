"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace, type InvitationDto } from "@/context/WorkspaceContext";
import { apiFetch } from "@/lib/api";

export default function JoinPage() {
  const params = useParams();
  const router = useRouter();
  const token = params?.token as string;

  const { isAuthenticated } = useAuth();
  const { acceptInvite } = useWorkspace();

  const [invitation, setInvitation] = useState<InvitationDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    if (typeof window !== "undefined") {
      sessionStorage.setItem("auth_redirect", `/join/${token}`);
    }
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
      const ws = await acceptInvite(token);
      if (typeof window !== "undefined") {
        sessionStorage.removeItem("auth_redirect");
      }
      router.push(`/w/${ws.slug}`);
    } catch (err: any) {
      setError(err?.message || "Failed to join workspace.");
      setJoining(false);
    }
  };

  const handleSignIn = () => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("auth_redirect", `/join/${token}`);
    }
    router.push(`/?redirect=/join/${token}`);
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
            <p className="text-xs text-outline mb-6">
              You have been invited to join this workspace as a{" "}
              <strong className="text-on-surface capitalize">
                {invitation?.role.toLowerCase() || "member"}
              </strong>
              .
            </p>

            {error && (
              <div className="w-full mb-6 p-3 rounded-2xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
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
                  onClick={handleSignIn}
                  className="w-full py-3 px-4 bg-primary text-white font-bold text-sm rounded-2xl hover:bg-primary/90 transition-all shadow-md shadow-primary/20"
                >
                  Sign In to Accept
                </button>
                <p className="text-[11px] text-outline">
                  Don&apos;t have an account? Sign in or sign up to join.
                </p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
