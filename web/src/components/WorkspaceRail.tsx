"use client";

import React from "react";
import { useWorkspace, type WorkspaceDto } from "@/context/WorkspaceContext";

interface WorkspaceRailProps {
  onOpenCreateWorkspace: () => void;
}

export default function WorkspaceRail({
  onOpenCreateWorkspace,
}: WorkspaceRailProps) {
  const { workspaces, currentWorkspace, switchWorkspace } = useWorkspace();

  return (
    <nav
      aria-label="Workspaces"
      className="w-16 md:w-18 shrink-0 bg-surface-container-lowest border-r border-outline-variant/15 flex flex-col items-center py-3.5 gap-3.5 z-30 select-none"
    >
      {/* App Logo */}
      <div className="w-11 h-11 rounded-2xl bg-primary flex items-center justify-center text-white shadow-md shadow-primary/20 mb-1 cursor-default font-black text-lg">
        M
      </div>

      <div className="w-8 h-px bg-outline-variant/20 rounded-full" />

      {/* Workspaces List */}
      <div className="flex-1 w-full flex flex-col items-center gap-3 overflow-y-auto no-scrollbar px-2">
        {workspaces.map((ws: WorkspaceDto) => {
          const isActive = currentWorkspace?.id === ws.id;
          const initial = ws.name.charAt(0).toUpperCase();

          return (
            <div
              key={ws.id}
              className="relative group flex items-center justify-center w-full"
            >
              {/* Active Indicator Bar */}
              <div
                className={`absolute left-0 w-1 bg-primary rounded-r-full transition-all duration-200 ${
                  isActive
                    ? "h-8"
                    : "h-0 group-hover:h-4 group-hover:bg-outline-variant"
                }`}
              />

              {/* Workspace Button */}
              <button
                type="button"
                onClick={() => switchWorkspace(ws.slug)}
                title={ws.name}
                className={`w-11 h-11 rounded-2xl flex items-center justify-center font-bold text-sm transition-all duration-200 shadow-sm ${
                  isActive
                    ? "bg-primary text-white rounded-2xl shadow-primary/25 shadow-md scale-105"
                    : "bg-surface-container hover:bg-surface-container-high text-on-surface hover:rounded-xl hover:scale-105"
                }`}
              >
                {ws.iconUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={ws.iconUrl}
                    alt={ws.name}
                    className="w-full h-full object-cover rounded-[inherit]"
                  />
                ) : (
                  initial
                )}
              </button>

              {/* Tooltip */}
              <div className="absolute left-full ml-3.5 hidden group-hover:flex items-center z-50 pointer-events-none">
                <div className="bg-inverse-surface text-inverse-on-surface text-xs font-semibold px-2.5 py-1.5 rounded-lg whitespace-nowrap shadow-lg">
                  {ws.name}
                  {ws.role === "OWNER" && (
                    <span className="ml-1.5 text-[10px] text-primary-fixed bg-primary/20 px-1 py-0.5 rounded">
                      Owner
                    </span>
                  )}
                </div>
              </div>
            </div>
          );
        })}

        {/* Add Workspace Button */}
        <div className="relative group flex items-center justify-center w-full">
          <button
            type="button"
            onClick={onOpenCreateWorkspace}
            title="Add Workspace"
            className="w-11 h-11 rounded-2xl border-2 border-dashed border-outline-variant/40 hover:border-primary flex items-center justify-center text-outline hover:text-primary hover:bg-primary/5 transition-all duration-200 hover:scale-105"
          >
            <span className="material-symbols-outlined text-xl">add</span>
          </button>

          {/* Tooltip */}
          <div className="absolute left-full ml-3.5 hidden group-hover:flex items-center z-50 pointer-events-none">
            <div className="bg-inverse-surface text-inverse-on-surface text-xs font-semibold px-2.5 py-1.5 rounded-lg whitespace-nowrap shadow-lg">
              Create Workspace
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
}
