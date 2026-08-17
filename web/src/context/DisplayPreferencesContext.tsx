"use client";

import React, { createContext, useContext, useEffect, useState } from "react";

export type AppearancePreference = "system" | "light" | "dark";
export type EffectiveAppearance = "light" | "dark";
export type InterfaceLanguage = "en" | "my";

export type DisplayPreferences = {
  appearance: AppearancePreference;
  language: InterfaceLanguage;
};

type DisplayPreferencesContextValue = {
  appearance: AppearancePreference;
  effectiveAppearance: EffectiveAppearance;
  language: InterfaceLanguage;
  setAppearance: (appearance: AppearancePreference) => void;
  setLanguage: (language: InterfaceLanguage) => void;
};

const STORAGE_KEY = "meow.displayPreferences";

const defaultPreferences: DisplayPreferences = {
  appearance: "system",
  language: "en",
};

const noop = () => {};

const DisplayPreferencesContext = createContext<DisplayPreferencesContextValue>(
  {
    ...defaultPreferences,
    effectiveAppearance: "light",
    setAppearance: noop,
    setLanguage: noop,
  }
);

const readStoredPreferences = (): DisplayPreferences => {
  if (typeof window === "undefined") return defaultPreferences;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultPreferences;
    const parsed = JSON.parse(raw);
    return {
      appearance:
        parsed.appearance === "light" ||
        parsed.appearance === "dark" ||
        parsed.appearance === "system"
          ? parsed.appearance
          : defaultPreferences.appearance,
      language:
        parsed.language === "my" || parsed.language === "en"
          ? parsed.language
          : defaultPreferences.language,
    };
  } catch {
    return defaultPreferences;
  }
};

const getSystemAppearance = (): EffectiveAppearance => {
  if (
    typeof window !== "undefined" &&
    window.matchMedia?.("(prefers-color-scheme: dark)").matches
  ) {
    return "dark";
  }
  return "light";
};

export function DisplayPreferencesProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [preferences, setPreferences] = useState<DisplayPreferences>(() =>
    readStoredPreferences()
  );
  const [systemAppearance, setSystemAppearance] = useState<EffectiveAppearance>(
    () => getSystemAppearance()
  );

  const { appearance, language } = preferences;

  const setAppearance = (newAppearance: AppearancePreference) =>
    setPreferences((prev) => ({ ...prev, appearance: newAppearance }));

  const setLanguage = (newLanguage: InterfaceLanguage) =>
    setPreferences((prev) => ({ ...prev, language: newLanguage }));

  const effectiveAppearance =
    appearance === "system" ? systemAppearance : appearance;

  useEffect(() => {
    const mediaQuery = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!mediaQuery) return;

    const syncSystemAppearance = (
      event: MediaQueryListEvent | MediaQueryList
    ) => setSystemAppearance(event.matches ? "dark" : "light");

    mediaQuery.addEventListener?.("change", syncSystemAppearance);
    return () =>
      mediaQuery.removeEventListener?.("change", syncSystemAppearance);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ appearance, language })
      );
    } catch {
      // Ignore storage errors in restricted environments
    }
  }, [appearance, language]);

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.appearance = effectiveAppearance;
    root.dataset.appearancePreference = appearance;
    root.dataset.interfaceLanguage = language;
    root.classList.toggle("dark", effectiveAppearance === "dark");
  }, [appearance, effectiveAppearance, language]);

  return (
    <DisplayPreferencesContext.Provider
      value={{
        appearance,
        effectiveAppearance,
        language,
        setAppearance,
        setLanguage,
      }}
    >
      {children}
    </DisplayPreferencesContext.Provider>
  );
}

export const useDisplayPreferences = () =>
  useContext(DisplayPreferencesContext);

export const displayPreferencesStorageKey = STORAGE_KEY;
