"use client";

import React, { useState } from "react";
import { useWorkspace, type InvitationDto } from "@/context/WorkspaceContext";

interface InviteMembersModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function InviteMembersModal({
  isOpen,
  onClose,
}: InviteMembersModalProps) {
  const { currentWorkspace, createEmailInvite, createJoinLink } =
    useWorkspace();

  const [tab, setTab] = useState<"email" | "link">("link");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
  const [generatedLink, setGeneratedLink] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !currentWorkspace) return null;

  const handleSendEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      await createEmailInvite(email.trim(), role);
      setSuccess(`Invitation email sent to ${email.trim()}`);
      setEmail("");
    } catch (err: any) {
      setError(err?.message || "Failed to send invitation");
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateLink = async () => {
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      const invite: InvitationDto = await createJoinLink();
      const origin =
        typeof window !== "undefined" ? window.location.origin : "";
      const link = `${origin}/join/${invite.token}`;
      setGeneratedLink(link);
    } catch (err: any) {
      setError(err?.message || "Failed to generate join link");
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (generatedLink) {
      navigator.clipboard.writeText(generatedLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined text-base">
                person_add
              </span>
            </div>
            <div>
              <h3 className="font-bold text-sm text-on-surface">
                Invite to {currentWorkspace.name}
              </h3>
              <p className="text-[11px] text-outline">
                Collaborate with colleagues in real-time
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        {/* Tab Buttons */}
        <div className="flex border-b border-outline-variant/15 px-6 gap-6 bg-surface-container-low/30">
          <button
            type="button"
            onClick={() => setTab("link")}
            className={`py-3 text-xs font-bold border-b-2 transition-all ${
              tab === "link"
                ? "border-primary text-primary"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Shareable Join Link
          </button>
          <button
            type="button"
            onClick={() => setTab("email")}
            className={`py-3 text-xs font-bold border-b-2 transition-all ${
              tab === "email"
                ? "border-primary text-primary"
                : "border-transparent text-outline hover:text-on-surface"
            }`}
          >
            Send Email Invite
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
          )}
          {success && (
            <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-xs font-medium">
              {success}
            </div>
          )}

          {tab === "link" ? (
            <div className="space-y-4">
              <p className="text-xs text-outline leading-relaxed">
                Anyone with this link can join{" "}
                <strong>{currentWorkspace.name}</strong> as a Member.
              </p>

              {generatedLink ? (
                <div className="space-y-2">
                  <div className="flex items-center gap-2 p-2 bg-surface-container rounded-2xl border border-outline-variant/30">
                    <input
                      type="text"
                      readOnly
                      value={generatedLink}
                      className="flex-1 bg-transparent px-2 text-xs font-mono text-on-surface focus:outline-none truncate"
                    />
                    <button
                      type="button"
                      onClick={handleCopy}
                      className="px-3 py-1.5 bg-primary text-white text-xs font-bold rounded-xl hover:bg-primary/90 transition-colors shrink-0 flex items-center gap-1.5"
                    >
                      <span className="material-symbols-outlined text-sm">
                        {copied ? "check" : "content_copy"}
                      </span>
                      {copied ? "Copied!" : "Copy"}
                    </button>
                  </div>
                  <span className="text-[10px] text-outline block">
                    Link is active for 7 days
                  </span>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={handleGenerateLink}
                  disabled={loading}
                  className="w-full py-2.5 px-4 bg-primary text-white font-bold text-xs rounded-xl hover:bg-primary/90 transition-colors shadow-sm disabled:opacity-50 flex items-center justify-center gap-2"
                >
                  <span className="material-symbols-outlined text-base">
                    link
                  </span>
                  {loading ? "Generating..." : "Generate Join Link"}
                </button>
              )}
            </div>
          ) : (
            <form onSubmit={handleSendEmail} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Email Address <span className="text-error">*</span>
                </label>
                <input
                  type="email"
                  required
                  placeholder="colleague@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-on-surface mb-1.5">
                  Workspace Role
                </label>
                <select
                  value={role}
                  onChange={(e) =>
                    setRole(e.target.value as "MEMBER" | "ADMIN")
                  }
                  className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
                >
                  <option value="MEMBER">Member (Standard Access)</option>
                  <option value="ADMIN">
                    Admin (Can manage channels and invitations)
                  </option>
                </select>
              </div>

              <div className="pt-2 flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 text-xs font-bold text-outline hover:text-on-surface hover:bg-surface-container rounded-xl transition-colors"
                >
                  Close
                </button>
                <button
                  type="submit"
                  disabled={loading || !email.trim()}
                  className="px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50"
                >
                  {loading ? "Sending..." : "Send Invitation"}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
