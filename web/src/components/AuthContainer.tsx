"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";

type FormMode = "login" | "signup";

export default function AuthContainer() {
  const [formMode, setFormMode] = useState<FormMode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // Verification Screen States
  const [verificationView, setVerificationView] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [usernameOrEmailForVerify, setUsernameOrEmailForVerify] = useState("");
  const [cooldown, setCooldown] = useState(0);

  const { login } = useAuth();

  useEffect(() => {
    if (cooldown > 0) {
      const timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldown]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (formMode === "signup" && password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setLoading(true);

    try {
      const endpoint =
        formMode === "login" ? "/api/v1/auth/login" : "/api/v1/auth/signup";
      const body =
        formMode === "login"
          ? JSON.stringify({ username, password })
          : JSON.stringify({
              username,
              password,
              email: email || undefined,
              fullName: fullName || undefined,
              birthDate: birthDate || undefined,
            });

      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        const errMsg = errorData?.message ||
          (formMode === "login"
            ? "Invalid username or password"
            : "Registration failed. Username may already exist.");

        if (formMode === "login" && errMsg === "Email is not verified. Please verify your email first.") {
          setUsernameOrEmailForVerify(username);
          setVerificationView(true);
          setCooldown(60);
          setLoading(false);
          return;
        }

        throw new Error(errMsg);
      }

      const data = await res.json();
      if (formMode === "signup" && email) {
        setUsernameOrEmailForVerify(username);
        setVerificationView(true);
        setCooldown(60);
      } else {
        login(data.token);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "An unexpected error occurred.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const res = await fetch("/api/v1/auth/verify-email", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          usernameOrEmail: usernameOrEmailForVerify,
          code: verificationCode,
        }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        throw new Error(errorData?.message || "Verification failed.");
      }

      const data = await res.json();
      login(data.token);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "An unexpected error occurred.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (cooldown > 0) return;
    setError("");
    setLoading(true);

    try {
      const res = await fetch("/api/v1/auth/resend-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          usernameOrEmail: usernameOrEmailForVerify,
        }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        throw new Error(errorData?.message || "Failed to resend code.");
      }

      setCooldown(60);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "An unexpected error occurred.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const switchForm = (mode: FormMode) => {
    setFormMode(mode);
    setError("");
    setUsername("");
    setPassword("");
    setEmail("");
    setShowPassword(false);
    setConfirmPassword("");
    setShowConfirmPassword(false);
  };

  return (
    <div className="flex items-center justify-center min-h-screen px-4 py-8 bg-background relative overflow-hidden">
      {/* Background decorative elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-96 h-96 bg-primary/3 rounded-full blur-3xl" />
        <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-tertiary/3 rounded-full blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-primary/[0.02] rounded-full blur-3xl" />
      </div>

      <div className="bg-surface-container-lowest p-8 md:p-10 outline outline-1 outline-outline-variant/30 rounded-2xl shadow-[0_24px_64px_rgba(0,66,117,0.08)] max-w-md w-full relative overflow-y-auto max-h-[calc(100vh-4rem)] z-10">
        {verificationView ? (
          <>
            {/* Verification Security Badge */}
            <div className="mb-8 p-4 bg-primary/5 rounded-xl border border-primary/10 text-[11px] leading-relaxed text-on-surface-variant flex gap-3">
              <span className="material-symbols-outlined text-primary text-xl shrink-0">
                lock_open
              </span>
              <div>
                <span className="font-bold text-primary block mb-0.5">
                  Email Activation Required
                </span>
                To complete access key provisioning, enter the 6-digit verification code printed to the offline ledger console.
              </div>
            </div>

            {/* Icon */}
            <div className="flex justify-center mb-4">
              <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center border border-outline-variant/30">
                <span className="material-symbols-outlined text-primary text-2xl">
                  mark_email_unread
                </span>
              </div>
            </div>

            <h1 className="text-2xl font-headline font-black text-center mb-1 tracking-tight text-on-surface">
              Verify Account
            </h1>
            <p className="text-center text-xs text-outline mb-6">
              Enter code for <span className="font-bold text-on-surface">{usernameOrEmailForVerify}</span>
            </p>

            {/* Error Display */}
            {error && (
              <div className="bg-error-container text-on-error-container text-xs p-3 rounded-lg mb-6 flex items-center gap-2 border border-error/10 animate-[fadeIn_0.2s_ease-out]">
                <span className="material-symbols-outlined text-error text-base">
                  error
                </span>
                {error}
              </div>
            )}

            {/* Verification Form */}
            <form onSubmit={handleVerifyCode} className="space-y-4">
              <div>
                <label
                  htmlFor="verification-code"
                  className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                >
                  Verification Code
                </label>
                <input
                  id="verification-code"
                  type="text"
                  required
                  maxLength={6}
                  pattern="[0-9]{6}"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, ""))}
                  className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container text-center tracking-[0.5em] font-mono text-lg transition-all outline-none"
                  placeholder="000000"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 mt-4 bg-primary hover:bg-primary-container text-white rounded-lg font-bold text-xs uppercase tracking-widest transition-all disabled:opacity-70 flex justify-center items-center gap-2 shadow-sm scale-98-active"
              >
                {loading ? (
                  <span className="material-symbols-outlined animate-spin text-sm">
                    rotate_right
                  </span>
                ) : (
                  <span className="material-symbols-outlined text-sm">
                    verified
                  </span>
                )}
                {loading ? "Activating..." : "Verify Code"}
              </button>

              <button
                type="button"
                disabled={loading || cooldown > 0}
                onClick={handleResendCode}
                className="w-full py-3 bg-surface-container-low hover:bg-surface-container text-primary rounded-lg font-bold text-xs uppercase tracking-widest transition-all disabled:opacity-50 flex justify-center items-center gap-2 border border-outline-variant/30"
              >
                <span className="material-symbols-outlined text-sm">
                  autorenew
                </span>
                {cooldown > 0 ? `Resend in ${cooldown}s` : "Resend Code"}
              </button>

              <button
                type="button"
                onClick={() => {
                  setVerificationView(false);
                  setError("");
                }}
                className="w-full text-center text-xs text-primary hover:underline font-bold mt-2"
              >
                Cancel
              </button>
            </form>
          </>
        ) : (
          <>
            {/* Anti-Phishing Security Badge */}
            <div className="mb-8 p-4 bg-primary/5 rounded-xl border border-primary/10 text-[11px] leading-relaxed text-on-surface-variant flex gap-3">
              <span className="material-symbols-outlined text-primary text-xl shrink-0">
                shield
              </span>
              <div>
                <span className="font-bold text-primary block mb-0.5">
                  {formMode === "login"
                    ? "Registry Security Verification"
                    : "Admin Provisioning Protocol"}
                </span>
                {formMode === "login"
                  ? "This gateway belongs to Meow Chit Chat. Access keys are provisioned offline by administrators. Verify that the URL matches your assigned local endpoint."
                  : "Curator accounts are generated strictly for authorized library personnel. All registered identity keys are logged in the offline ledger. Verify local encryption before submitting."}
              </div>
            </div>

            {/* Form Mode Toggle */}
            <div className="flex bg-surface-container-low rounded-xl p-1 mb-6 gap-1">
              <button
                type="button"
                onClick={() => switchForm("login")}
                className={`flex-1 py-2.5 rounded-lg text-xs font-bold uppercase tracking-widest transition-all duration-300 ${
                  formMode === "login"
                    ? "bg-surface-container-lowest text-primary shadow-sm"
                    : "text-outline hover:text-on-surface-variant"
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => switchForm("signup")}
                className={`flex-1 py-2.5 rounded-lg text-xs font-bold uppercase tracking-widest transition-all duration-300 ${
                  formMode === "signup"
                    ? "bg-surface-container-lowest text-primary shadow-sm"
                    : "text-outline hover:text-on-surface-variant"
                }`}
              >
                Register
              </button>
            </div>

            {/* Icon */}
            <div className="flex justify-center mb-4">
              <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center border border-outline-variant/30">
                <span className="material-symbols-outlined text-primary text-2xl">
                  {formMode === "login" ? "key" : "person_add"}
                </span>
              </div>
            </div>

            <h1 className="text-2xl font-headline font-black text-center mb-1 tracking-tight text-on-surface">
              {formMode === "login" ? "Access Registry" : "Provision Identity"}
            </h1>
            <p className="text-center text-xs text-outline mb-6">
              {formMode === "login"
                ? "Enter your administrator-provided credentials."
                : "Create a new authenticated curator key."}
            </p>

            {/* Error Display */}
            {error && (
              <div className="bg-error-container text-on-error-container text-xs p-3 rounded-lg mb-6 flex items-center gap-2 border border-error/10 animate-[fadeIn_0.2s_ease-out]">
                <span className="material-symbols-outlined text-error text-base">
                  error
                </span>
                {error}
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label
                  htmlFor="auth-username"
                  className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                >
                  Username
                </label>
                <input
                  id="auth-username"
                  type="text"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                  placeholder={
                    formMode === "login" ? "e.g. meow_01" : "e.g. meow_02"
                  }
                />
              </div>

              {formMode === "signup" && (
                <>
                  <div>
                    <label
                      htmlFor="auth-email"
                      className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                    >
                      Email (Optional)
                    </label>
                    <input
                      id="auth-email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="meow@chatchat.net"
                    />
                  </div>

                  <div>
                    <label
                      htmlFor="auth-fullname"
                      className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                    >
                      Full Name (Optional)
                    </label>
                    <input
                      id="auth-fullname"
                      type="text"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="e.g. John Doe"
                    />
                  </div>

                  <div>
                    <label
                      htmlFor="auth-birthdate"
                      className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                    >
                      Date of Birth (Optional)
                    </label>
                    <input
                      id="auth-birthdate"
                      type="date"
                      value={birthDate}
                      onChange={(e) => setBirthDate(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                    />
                  </div>
                </>
              )}

              <div>
                <label
                  htmlFor="auth-password"
                  className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                >
                  Password
                </label>
                <div className="relative">
                  <input
                    id="auth-password"
                    type={showPassword ? "text" : "password"}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 pr-12 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                    placeholder="••••••••"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-on-surface focus:outline-none flex items-center justify-center p-1"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    <span className="material-symbols-outlined text-[20px]">
                      {showPassword ? "visibility_off" : "visibility"}
                    </span>
                  </button>
                </div>
              </div>

              {formMode === "signup" && (
                <div>
                  <label
                    htmlFor="auth-confirm-password"
                    className="block text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5"
                  >
                    Re-enter Password
                  </label>
                  <div className="relative">
                    <input
                      id="auth-confirm-password"
                      type={showConfirmPassword ? "text" : "password"}
                      required
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      className="w-full bg-surface-container-low border border-transparent rounded-lg py-3 px-4 pr-12 text-sm text-on-surface focus:border-primary focus:bg-surface-container transition-all outline-none"
                      placeholder="••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-on-surface focus:outline-none flex items-center justify-center p-1"
                      aria-label={showConfirmPassword ? "Hide confirm password" : "Show confirm password"}
                    >
                      <span className="material-symbols-outlined text-[20px]">
                        {showConfirmPassword ? "visibility_off" : "visibility"}
                      </span>
                    </button>
                  </div>
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 mt-4 bg-primary hover:bg-primary-container text-white rounded-lg font-bold text-xs uppercase tracking-widest transition-all disabled:opacity-70 flex justify-center items-center gap-2 shadow-sm scale-98-active"
              >
                {loading ? (
                  <span className="material-symbols-outlined animate-spin text-sm">
                    rotate_right
                  </span>
                ) : (
                  <span className="material-symbols-outlined text-sm">
                    {formMode === "login" ? "login" : "how_to_reg"}
                  </span>
                )}
                {loading
                  ? formMode === "login"
                    ? "Decrypting Vault..."
                    : "Generating Vault..."
                  : formMode === "login"
                    ? "Sign In"
                    : "Register Key"}
              </button>
            </form>
          </>
        )}

        {/* Security Signature */}
        <div className="mt-8 text-[9px] text-center text-outline/50 uppercase tracking-widest font-label">
          Ledger Control Auth System v2026.1
        </div>
      </div>
    </div>
  );
}
