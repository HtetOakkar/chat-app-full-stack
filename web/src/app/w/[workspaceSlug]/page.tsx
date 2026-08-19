"use client";

import React, { useEffect } from "react";
import { useParams } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import AuthContainer from "@/components/AuthContainer";
import ChatDashboard from "@/components/ChatDashboard";

export default function WorkspacePage() {
  const params = useParams();
  const workspaceSlug = params?.workspaceSlug as string;
  const { isLoading, isAuthenticated } = useAuth();
  const { switchWorkspace, currentWorkspace } = useWorkspace();

  useEffect(() => {
    if (
      !isLoading &&
      isAuthenticated &&
      workspaceSlug &&
      currentWorkspace?.slug !== workspaceSlug
    ) {
      switchWorkspace(workspaceSlug);
    }
  }, [
    isLoading,
    isAuthenticated,
    workspaceSlug,
    currentWorkspace?.slug,
    switchWorkspace,
  ]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-dvh bg-background">
        <div className="flex flex-col items-center gap-4">
          <span className="material-symbols-outlined animate-spin text-4xl text-primary">
            rotate_right
          </span>
          <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-outline">
            Loading Workspace...
          </span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <AuthContainer />;
  }

  return <ChatDashboard />;
}
