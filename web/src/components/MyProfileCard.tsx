"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";

interface MyProfileCardProps {
  onBack: () => void;
}

interface ProfileData {
  username: string;
  email: string | null;
  fullName: string | null;
  birthDate: string | null;
  emailVerified: boolean;
}

export default function MyProfileCard({ onBack }: MyProfileCardProps) {
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [fullName, setFullName] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [email, setEmail] = useState("");
  const [sharePresence, setSharePresence] = useState(true);

  // Verification fields (for subsequent slices)
  const [verificationCode, setVerificationCode] = useState("");
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [verificationError, setVerificationError] = useState("");
  const [verificationSuccess, setVerificationSuccess] = useState("");

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const fetchProfile = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await apiFetch("/api/v1/users/profile");
      if (data) {
        setProfile(data);
        setFullName(data.fullName || "");
        setBirthDate(data.birthDate || "");
        setEmail(data.email || "");
      }

      const settingsData = await apiFetch("/api/v1/users/settings");
      if (settingsData) {
        setSharePresence(settingsData.sharePresence);
      }
    } catch (err: unknown) {
      setError("Failed to load profile details.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
    setSuccess("");
    setVerificationError("");
    setVerificationSuccess("");
  }, []);

  // Handle countdown for resend cooldown
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setTimeout(() => {
      setResendCooldown((prev) => prev - 1);
    }, 1000);
    return () => clearTimeout(timer);
  }, [resendCooldown]);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const data = await apiFetch("/api/v1/users/profile", {
        method: "PUT",
        body: JSON.stringify({
          fullName: fullName.trim() || null,
          birthDate: birthDate || null,
          email: email.trim() || null,
        }),
      });
      if (data) {
        setProfile(data);
        setFullName(data.fullName || "");
        setBirthDate(data.birthDate || "");
        setEmail(data.email || "");

        await apiFetch("/api/v1/users/settings", {
          method: "PUT",
          body: JSON.stringify({ sharePresence }),
        });

        setSuccess("Profile and settings updated successfully.");
        // If email was changed, trigger refresh to update verification state
        if (data.email !== profile?.email) {
          setVerificationError("");
          setVerificationSuccess("");
        }
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Failed to update profile."
      );
    } finally {
      setSaving(false);
    }
  };

  const handleVerifyEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!verificationCode || verificationCode.length !== 6) {
      setVerificationError("Code must be 6 digits.");
      return;
    }
    setVerifyLoading(true);
    setVerificationError("");
    setVerificationSuccess("");
    try {
      await apiFetch("/api/v1/users/profile/verify-email", {
        method: "POST",
        body: JSON.stringify({ code: verificationCode }),
      });
      setVerificationSuccess("Email verified successfully!");
      setVerificationCode("");
      fetchProfile();
    } catch (err: unknown) {
      setVerificationError(
        err instanceof Error ? err.message : "Failed to verify email."
      );
    } finally {
      setVerifyLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (resendCooldown > 0) return;
    setResendLoading(true);
    setVerificationError("");
    setVerificationSuccess("");
    try {
      await apiFetch("/api/v1/users/profile/resend-code", {
        method: "POST",
      });
      setVerificationSuccess("Verification code sent to your email!");
      setResendCooldown(60);
    } catch (err: unknown) {
      setVerificationError(
        err instanceof Error ? err.message : "Failed to resend code."
      );
    } finally {
      setResendLoading(false);
    }
  };

  return (
    <div className="flex-1 flex justify-center overflow-y-auto bg-background custom-scrollbar">
      <div className="w-full max-w-2xl px-4 py-6 md:py-10">
        {/* Back Button */}
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-outline hover:text-on-surface mb-6 transition-colors"
        >
          <span className="material-symbols-outlined text-lg">arrow_back</span>
          <span className="text-sm font-medium">Back to Chat</span>
        </button>

        {loading && !profile ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3">
            <span className="material-symbols-outlined animate-spin text-primary text-3xl">
              rotate_right
            </span>
            <span className="text-sm text-outline font-bold uppercase tracking-wider">
              Loading Profile...
            </span>
          </div>
        ) : (
          <div className="bg-surface-container-lowest outline outline-1 outline-outline-variant/30 rounded-2xl shadow-[0_24px_64px_rgba(0,66,117,0.06)] overflow-hidden flex flex-col">
            {/* Cover Banner */}
            <div className="h-32 bg-gradient-to-r from-primary/20 via-primary/10 to-transparent relative">
              <div className="absolute inset-0 bg-gradient-to-t from-surface-container-lowest/80 to-transparent" />
            </div>

            {/* Profile Info Section with Overlapping Avatar */}
            <div className="px-6 pb-6 relative">
              <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 -mt-12 mb-6">
                <div className="w-24 h-24 rounded-2xl bg-surface-container-lowest p-1 shadow-lg border border-outline-variant/20 shrink-0">
                  <div className="w-full h-full rounded-xl bg-primary/10 flex items-center justify-center text-primary font-black text-3xl">
                    {profile?.username
                      ? profile.username.charAt(0).toUpperCase()
                      : "?"}
                  </div>
                </div>
                <div className="flex-1 text-center sm:text-left mt-2">
                  <h2 className="text-xl font-bold text-on-surface leading-tight">
                    {profile?.fullName ||
                      `@${profile?.username || "loading..."}`}
                  </h2>
                  {profile?.fullName && (
                    <p className="text-sm font-medium text-outline mt-1">
                      @{profile.username}
                    </p>
                  )}
                </div>
              </div>

              {/* Alert Logs */}
              {error && (
                <div className="bg-error-container text-on-error-container text-xs p-3 rounded-lg flex items-center gap-2 border border-error/10 mb-6">
                  <span className="material-symbols-outlined text-error text-base">
                    error
                  </span>
                  {error}
                </div>
              )}
              {success && (
                <div className="bg-primary/5 text-primary text-xs p-3 rounded-lg flex items-center gap-2 border border-primary/10 mb-6">
                  <span className="material-symbols-outlined text-primary text-base">
                    check_circle
                  </span>
                  {success}
                </div>
              )}

              <form onSubmit={handleUpdateProfile} className="space-y-5">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                  <div>
                    <label
                      htmlFor="profile-fullname"
                      className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                    >
                      Full Name
                    </label>
                    <input
                      id="profile-fullname"
                      type="text"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="e.g. John Doe"
                    />
                  </div>
                  <div>
                    <label
                      htmlFor="profile-birthdate"
                      className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                    >
                      Date of Birth
                    </label>
                    <input
                      id="profile-birthdate"
                      type="date"
                      value={birthDate}
                      onChange={(e) => setBirthDate(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label
                    htmlFor="profile-email"
                    className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                  >
                    Email Address
                  </label>
                  <div className="flex flex-col sm:flex-row gap-3">
                    <input
                      id="profile-email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="flex-1 bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="e.g. john@chatapp.com"
                    />
                    {profile?.email && (
                      <span
                        className={`px-4 py-3 rounded-lg text-[10px] font-black uppercase tracking-wider flex items-center justify-center gap-1.5 border shrink-0 ${
                          profile.emailVerified
                            ? "bg-primary/10 text-primary border-primary/20"
                            : "bg-error-container text-on-error-container border-error/15"
                        }`}
                      >
                        <span className="material-symbols-outlined text-[14px]">
                          {profile.emailVerified
                            ? "verified"
                            : "pending_actions"}
                        </span>
                        {profile.emailVerified ? "Verified" : "Unverified"}
                      </span>
                    )}
                  </div>
                </div>

                <div className="pt-4 border-t border-outline-variant/10">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-on-surface mb-3 flex items-center gap-2">
                    <span className="material-symbols-outlined text-base">
                      security
                    </span>
                    Privacy Settings
                  </h3>
                  <div className="flex items-center justify-between bg-surface-container-low border border-transparent rounded-lg py-3 px-4 transition-all">
                    <div>
                      <div className="text-sm font-semibold text-on-surface">
                        Share Online Status
                      </div>
                      <div className="text-xs text-outline mt-0.5">
                        Allow others to see when you are online and typing.
                      </div>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        className="sr-only peer"
                        checked={sharePresence}
                        onChange={(e) => setSharePresence(e.target.checked)}
                      />
                      <div className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
                    </label>
                  </div>
                </div>

                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={saving}
                    className="w-full sm:w-auto px-8 py-3.5 bg-primary hover:bg-primary-container text-white rounded-xl font-bold text-xs uppercase tracking-widest transition-all disabled:opacity-75 flex justify-center items-center gap-2 shadow-sm"
                  >
                    {saving ? (
                      <span className="material-symbols-outlined animate-spin text-base">
                        rotate_right
                      </span>
                    ) : (
                      <span className="material-symbols-outlined text-base">
                        save
                      </span>
                    )}
                    {saving ? "Saving Changes..." : "Save Changes"}
                  </button>
                </div>
              </form>

              {/* Verification Section */}
              {profile?.email && !profile.emailVerified && (
                <div className="mt-8 border-t border-outline-variant/10 pt-8 space-y-4 animate-[fadeIn_0.3s_ease-out]">
                  <div className="p-5 bg-error-container/20 border border-error/10 rounded-xl flex gap-4">
                    <span className="material-symbols-outlined text-error text-2xl shrink-0">
                      mark_email_unread
                    </span>
                    <div>
                      <span className="font-bold text-error text-sm block mb-1">
                        Email Verification Required
                      </span>
                      <span className="text-xs text-on-surface-variant leading-relaxed block">
                        Verify your email to enable security logins with your
                        email key. Enter the 6-digit code logged in the curator
                        console.
                      </span>
                    </div>
                  </div>

                  {verificationError && (
                    <div className="bg-error-container text-on-error-container text-xs p-3 rounded-lg flex items-center gap-2 border border-error/10">
                      <span className="material-symbols-outlined text-error text-base">
                        error
                      </span>
                      {verificationError}
                    </div>
                  )}
                  {verificationSuccess && (
                    <div className="bg-primary/5 text-primary text-xs p-3 rounded-lg flex items-center gap-2 border border-primary/10">
                      <span className="material-symbols-outlined text-primary text-base">
                        check_circle
                      </span>
                      {verificationSuccess}
                    </div>
                  )}

                  <form
                    onSubmit={handleVerifyEmail}
                    className="flex flex-col sm:flex-row gap-3"
                  >
                    <input
                      type="text"
                      maxLength={6}
                      value={verificationCode}
                      onChange={(e) =>
                        setVerificationCode(e.target.value.replace(/\D/g, ""))
                      }
                      className="w-full sm:w-40 bg-surface-container-low border border-transparent rounded-xl py-3 px-4 text-center text-base font-bold tracking-[0.3em] placeholder:tracking-normal text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="000000"
                    />
                    <button
                      type="submit"
                      disabled={verifyLoading || verificationCode.length !== 6}
                      className="flex-1 py-3 px-6 bg-primary text-white text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-primary-container transition-all disabled:opacity-50"
                    >
                      {verifyLoading ? "Verifying..." : "Verify Code"}
                    </button>
                    <button
                      type="button"
                      disabled={resendLoading || resendCooldown > 0}
                      onClick={handleResendCode}
                      className="px-6 py-3 border border-outline-variant/30 text-on-surface text-xs font-bold uppercase tracking-widest rounded-xl hover:bg-surface-container transition-all disabled:opacity-50 shrink-0"
                    >
                      {resendLoading
                        ? "Sending..."
                        : resendCooldown > 0
                          ? `Resend (${resendCooldown}s)`
                          : "Resend"}
                    </button>
                  </form>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
