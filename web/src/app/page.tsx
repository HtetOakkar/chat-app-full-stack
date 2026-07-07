"use client";

import { useAuth } from "@/context/AuthContext";
import AuthContainer from "@/components/AuthContainer";
import ChatDashboard from "@/components/ChatDashboard";

export default function Home() {
  const { isLoading, isAuthenticated } = useAuth();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-dvh bg-background">
        <div className="flex flex-col items-center gap-4">
          <span className="material-symbols-outlined animate-spin text-4xl text-primary">
            rotate_right
          </span>
          <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-outline">
            Initializing Secure Gateway...
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
