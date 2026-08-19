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
  const { createWorkspace } = useWorkspace();

  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceSlug, setWorkspaceSlug] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleClose = () => {
    setError(null);
    setWorkspaceName("");
    setWorkspaceSlug("");
    onClose();
  };

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
      handleClose();
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create workspace";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-6 pb-2">
          <div>
            <h2 className="text-base font-bold text-on-surface">
              Create Workspace
            </h2>
            <p className="text-xs text-outline mt-0.5">
              Workspaces are shared environments for Members to collaborate.
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            aria-label="Close"
            className="w-8 h-8 rounded-xl flex items-center justify-center text-outline hover:text-on-surface hover:bg-surface-container transition-colors"
          >
            <span className="material-symbols-outlined text-lg">close</span>
          </button>
        </div>

        {/* Body */}
        <div className="p-6 pt-4">
          {error && (
            <div className="mb-4 p-3 rounded-xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
          )}

          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label
                htmlFor="workspace-name-input"
                className="block text-xs font-bold text-on-surface mb-1.5"
              >
                Workspace Name <span className="text-error">*</span>
              </label>
              <input
                id="workspace-name-input"
                type="text"
                required
                placeholder="e.g. Acme Corp, Engineering Hub"
                value={workspaceName}
                onChange={(e) => setWorkspaceName(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
              />
            </div>

            <div>
              <label
                htmlFor="workspace-slug-input"
                className="block text-xs font-bold text-on-surface mb-1.5"
              >
                URL Slug{" "}
                <span className="text-outline font-normal">(Optional)</span>
              </label>
              <input
                id="workspace-slug-input"
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
                onClick={handleClose}
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
        </div>
      </div>
    </div>
  );
}
