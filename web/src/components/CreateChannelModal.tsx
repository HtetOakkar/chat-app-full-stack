"use client";

import React, { useState } from "react";
import { useWorkspace } from "@/context/WorkspaceContext";

interface CreateChannelModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CreateChannelModal({
  isOpen,
  onClose,
}: CreateChannelModalProps) {
  const { createChannel } = useWorkspace();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isPrivate, setIsPrivate] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setError(null);
    setLoading(true);

    try {
      await createChannel(
        name.trim(),
        isPrivate ? "PRIVATE" : "PUBLIC",
        description.trim() || undefined
      );
      setName("");
      setDescription("");
      setIsPrivate(false);
      onClose();
    } catch (err: any) {
      setError(err?.message || "Failed to create channel");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-surface-container-lowest border border-outline-variant/20 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden flex flex-col">
        {/* Modal Header */}
        <div className="px-6 py-5 border-b border-outline-variant/15 bg-surface-container-low/50 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center text-primary font-bold">
              <span className="material-symbols-outlined text-base">tag</span>
            </div>
            <h3 className="font-bold text-sm text-on-surface">
              Create a Channel
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container-high flex items-center justify-center transition-colors"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        </div>

        {/* Modal Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-error-container/20 border border-error/20 text-error text-xs font-medium">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-on-surface mb-1.5">
              Channel Name <span className="text-error">*</span>
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-outline text-sm font-bold">
                #
              </span>
              <input
                type="text"
                required
                placeholder="e.g. announcements, project-alpha"
                value={name}
                onChange={(e) =>
                  setName(e.target.value.toLowerCase().replace(/\s+/g, "-"))
                }
                className="w-full pl-8 pr-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-on-surface mb-1.5">
              Description{" "}
              <span className="text-outline font-normal">(Optional)</span>
            </label>
            <textarea
              rows={2}
              placeholder="What is this channel about?"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-surface-container rounded-xl border border-outline-variant/30 text-sm focus:outline-none focus:border-primary transition-colors resize-none"
            />
          </div>

          {/* Privacy Toggle */}
          <div className="flex items-center justify-between p-3.5 rounded-2xl bg-surface-container border border-outline-variant/20">
            <div className="flex flex-col">
              <span className="text-xs font-bold text-on-surface">
                Private Channel
              </span>
              <span className="text-[11px] text-outline">
                Only invited colleagues can view and send messages
              </span>
            </div>
            <button
              type="button"
              onClick={() => setIsPrivate(!isPrivate)}
              className={`w-11 h-6 rounded-full transition-colors relative ${
                isPrivate ? "bg-primary" : "bg-outline-variant/50"
              }`}
            >
              <div
                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                  isPrivate ? "translate-x-6" : "translate-x-1"
                }`}
              />
            </button>
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
              disabled={loading || !name.trim()}
              className="px-5 py-2 text-xs font-bold bg-primary text-white rounded-xl hover:bg-primary/90 transition-all shadow-sm disabled:opacity-50"
            >
              {loading ? "Creating..." : "Create Channel"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
