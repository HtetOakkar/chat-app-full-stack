"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import AuthContainer from "@/components/AuthContainer";
import ChatDashboard from "@/components/ChatDashboard";
import { useTranslations } from "@/lib/i18n";

export default function Home() {
  const router = useRouter();
  const { isLoading, isAuthenticated } = useAuth();
  const t = useTranslations();

  useEffect(() => {
    if (isAuthenticated && typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      const redirectUrl =
        params.get("redirect") || sessionStorage.getItem("auth_redirect");
      if (redirectUrl && redirectUrl.startsWith("/")) {
        sessionStorage.removeItem("auth_redirect");
        router.push(redirectUrl);
      }
    }
  }, [isAuthenticated, router]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-dvh bg-background">
        <div className="flex flex-col items-center gap-4">
          <span className="material-symbols-outlined animate-spin text-4xl text-primary">
            rotate_right
          </span>
          <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-outline">
            {t.loadingApp}
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
