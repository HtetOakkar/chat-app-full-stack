"use client";

import React, { createContext, useContext, useEffect, useState } from "react";

export type AppearancePreference = "system" | "light" | "dark";
export type EffectiveAppearance = "light" | "dark";
export type InterfaceLanguage = "en" | "my";

type DisplayPreferencesContextValue = {
  appearance: AppearancePreference;
  effectiveAppearance: EffectiveAppearance;
  language: InterfaceLanguage;
  setAppearance: (appearance: AppearancePreference) => void;
  setLanguage: (language: InterfaceLanguage) => void;
};

const STORAGE_KEY = "meow.displayPreferences";

const defaultPreferences = {
  appearance: "system" as AppearancePreference,
  language: "en" as InterfaceLanguage,
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

const readStoredPreferences = () => {
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
  const [appearance, setAppearance] = useState<AppearancePreference>(
    defaultPreferences.appearance
  );
  const [language, setLanguage] = useState<InterfaceLanguage>(
    defaultPreferences.language
  );
  const [systemAppearance, setSystemAppearance] =
    useState<EffectiveAppearance>("light");
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);

  const effectiveAppearance =
    appearance === "system" ? systemAppearance : appearance;

  useEffect(() => {
    const storedPreferences = readStoredPreferences();
    setAppearance(storedPreferences.appearance);
    setLanguage(storedPreferences.language);
    setSystemAppearance(getSystemAppearance());
    setPreferencesLoaded(true);

    const mediaQuery = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!mediaQuery) return;

    const syncSystemAppearance = (
      event: MediaQueryListEvent | MediaQueryList
    ) => setSystemAppearance(event.matches ? "dark" : "light");

    syncSystemAppearance(mediaQuery);
    mediaQuery.addEventListener?.("change", syncSystemAppearance);
    return () =>
      mediaQuery.removeEventListener?.("change", syncSystemAppearance);
  }, []);

  useEffect(() => {
    if (!preferencesLoaded) return;
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ appearance, language })
    );
  }, [appearance, language, preferencesLoaded]);

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
