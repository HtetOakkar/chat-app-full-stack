"use client";

import {
  AppearancePreference,
  useDisplayPreferences,
} from "@/context/DisplayPreferencesContext";
import { useTranslations } from "@/lib/i18n";

export default function DisplayPreferencesControls({
  compact = false,
}: {
  compact?: boolean;
}) {
  const { appearance, language, setAppearance, setLanguage } =
    useDisplayPreferences();
  const t = useTranslations();

  const appearanceOptions: { value: AppearancePreference; label: string }[] = [
    { value: "system", label: t.system },
    { value: "light", label: t.light },
    { value: "dark", label: t.dark },
  ];
  const compactControlClass =
    "relative flex h-8 items-center gap-1.5 rounded-md border border-transparent bg-surface-container-low px-2 text-on-surface transition-colors hover:border-outline-variant/30 hover:bg-surface-container focus-within:border-primary focus-within:bg-surface-container-lowest";
  const compactSelectClass =
    "appearance-none bg-transparent py-1 pl-0 pr-5 text-[11px] font-bold text-on-surface outline-none";
  const settingsSelectClass =
    "h-10 w-full appearance-none rounded-lg border border-outline-variant/25 bg-surface-container-lowest px-3 pr-9 text-sm font-bold text-on-surface outline-none transition-colors hover:border-outline/40 focus:border-primary focus:bg-surface-container-low";

  if (!compact) {
    return (
      <div
        role="group"
        aria-label={t.preferences}
        className="divide-y divide-outline-variant/15 border-y border-outline-variant/15"
      >
        <div className="grid gap-3 py-4 md:grid-cols-[minmax(0,1fr)_13rem] md:items-center">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[17px]"
              >
                contrast
              </span>
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-on-surface">
                {t.appearance}
              </h3>
              <p className="mt-1 text-xs text-outline">
                System, light, or dark mode
              </p>
            </div>
          </div>
          <label className="relative block">
            <span className="sr-only">{t.appearance}</span>
            <select
              value={appearance}
              onChange={(event) =>
                setAppearance(event.target.value as AppearancePreference)
              }
              className={settingsSelectClass}
            >
              {appearanceOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <span
              aria-hidden="true"
              className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[16px] text-outline"
            >
              expand_more
            </span>
          </label>
        </div>

        <div className="grid gap-3 py-4 md:grid-cols-[minmax(0,1fr)_13rem] md:items-center">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[17px]"
              >
                translate
              </span>
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-on-surface">
                {t.interfaceLanguage}
              </h3>
              <p className="mt-1 text-xs text-outline">
                Language used across the interface
              </p>
            </div>
          </div>
          <label className="relative block">
            <span className="sr-only">{t.interfaceLanguage}</span>
            <select
              value={language}
              onChange={(event) =>
                setLanguage(event.target.value as "en" | "my")
              }
              className={settingsSelectClass}
            >
              <option value="en">{t.english}</option>
              <option value="my">{t.burmese}</option>
            </select>
            <span
              aria-hidden="true"
              className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[16px] text-outline"
            >
              expand_more
            </span>
          </label>
        </div>
      </div>
    );
  }

  return (
    <div
      role="toolbar"
      aria-label={t.preferences}
      className="inline-flex items-center gap-1 rounded-lg border border-outline-variant/20 bg-surface-container-lowest/80 p-1 shadow-sm"
    >
      <label className={compactControlClass}>
        <span
          aria-hidden="true"
          className="material-symbols-outlined text-[16px] text-primary"
        >
          contrast
        </span>
        <span className="sr-only">{t.appearance}</span>
        <select
          value={appearance}
          onChange={(event) =>
            setAppearance(event.target.value as AppearancePreference)
          }
          className={`${compactSelectClass} w-[4.9rem]`}
        >
          {appearanceOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <span
          aria-hidden="true"
          className="material-symbols-outlined pointer-events-none absolute right-1.5 text-[15px] text-outline"
        >
          expand_more
        </span>
      </label>

      <label className={compactControlClass}>
        <span
          aria-hidden="true"
          className="material-symbols-outlined text-[16px] text-primary"
        >
          translate
        </span>
        <span className="sr-only">{t.interfaceLanguage}</span>
        <select
          value={language}
          onChange={(event) => setLanguage(event.target.value as "en" | "my")}
          className={`${compactSelectClass} w-[5.3rem]`}
        >
          <option value="en">{t.english}</option>
          <option value="my">{t.burmese}</option>
        </select>
        <span
          aria-hidden="true"
          className="material-symbols-outlined pointer-events-none absolute right-1.5 text-[15px] text-outline"
        >
          expand_more
        </span>
      </label>
    </div>
  );
}
