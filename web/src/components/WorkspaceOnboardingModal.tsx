"use client";

import React, { useState } from "react";
import { useWorkspace } from "@/context/WorkspaceContext";

interface WorkspaceOnboardingModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function WorkspaceOnboardingModal({
  isOpen,
  onClose,
}: WorkspaceOnboardingModalProps) {
  const { createWorkspace, acceptInvite } = useWorkspace();

  const [mode, setMode] = useState<"create" | "join">("create");
  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceSlug, setWorkspaceSlug] = useState("");
  const [inviteToken, setInviteToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!workspaceName.trim()) return;
    setError(null);
    setLoading(true);

    try {
      await createWorkspace(
        workspaceName.trim(),
        workspaceSlug.trim() || undefined
      );
      onClose();
    } catch (err: any) {
      setError(err?.message || "Failed to create workspace");
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteToken.trim()) return;
    setError(null);
    setLoading(true);

    try {
      // If full URL was pasted, extract token
      const token = inviteToken.trim().split("/").pop() || inviteToken.trim();
      await acceptInvite(token);
      onClose();
    } catch (err: any) {
      setError(err?.message || "Failed to join workspace");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden flex flex-col">
        {/* Header Tabs */}
        <div className="flex border-b border-outline-variant/15 bg-surface-container-low/50">
          <button
            type="button"
            onClick={() => {
              setMode("create");
              setError(null);
            }}
            className={`flex-1 py-4 text-xs font-bold transition-all border-b-2 ${
              mode === "create"
                ? "border-primary text-primary bg-surface-container-lowest"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Create Workspace
          </button>
          <button
            type="button"
            onClick={() => {
              setMode("join");
              setError(null);
            }}
            className={`flex-1 py-4 text-xs font-bold transition-all border-b-2 ${
              mode === "join"
                ? "border-primary text-primary bg-surface-container-lowest"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Join with Invite
          </button>
        </div>

        {/* Body */}
        <div className="p-6">
          {error && (
            <div className="mb-4 p-3 rounded-xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
          )}

          {mode === "create" ? (
            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Workspace Name <span className="text-error">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Acme Corp, Engineering Hub"
                  value={workspaceName}
                  onChange={(e) => setWorkspaceName(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  URL Slug{" "}
                  <span className="text-outline font-normal">(Optional)</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. acme-corp"
                  value={workspaceSlug}
                  onChange={(e) => setWorkspaceSlug(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
                />
              </div>

              <div className="pt-3 flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 text-xs font-bold text-outline hover:text-on-surface hover:bg-surface-container rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading || !workspaceName.trim()}
                  className="px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50"
                >
                  {loading ? "Creating..." : "Create Workspace"}
                </button>
              </div>
            </form>
          ) : (
            <form onSubmit={handleJoin} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Invitation Token or Link <span className="text-error">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Paste invite link or token"
                  value={inviteToken}
                  onChange={(e) => setInviteToken(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
                />
              </div>

              <div className="pt-3 flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 text-xs font-bold text-outline hover:text-on-surface hover:bg-surface-container rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading || !inviteToken.trim()}
                  className="px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50"
                >
                  {loading ? "Joining..." : "Join Workspace"}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
