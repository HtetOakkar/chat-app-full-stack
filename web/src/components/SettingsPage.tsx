"use client";

import DisplayPreferencesControls from "./DisplayPreferencesControls";

export default function SettingsPage({ onBack }: { onBack: () => void }) {
  return (
    <main
      aria-label="Settings"
      className="flex-1 overflow-y-auto bg-background"
    >
      <div className="mx-auto flex min-h-full w-full max-w-5xl flex-col px-4 py-5 md:px-8 md:py-8">
        <header className="mb-6 flex items-center justify-between gap-4 border-b border-outline-variant/15 pb-5">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              onClick={onBack}
              aria-label="Back to chat"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-outline-variant/20 bg-surface-container-lowest text-on-surface transition-colors hover:bg-surface-container-low"
            >
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[18px]"
              >
                arrow_back
              </span>
            </button>
            <div className="min-w-0">
              <h1 className="truncate text-xl font-black tracking-tight text-on-surface md:text-2xl">
                Settings
              </h1>
              <p className="mt-1 text-[10px] font-bold uppercase tracking-[0.15em] text-outline">
                Account Controls
              </p>
            </div>
          </div>
        </header>

        <div className="grid flex-1 gap-6 md:grid-cols-[12rem_minmax(0,1fr)]">
          <nav
            aria-label="Settings sections"
            className="flex gap-2 overflow-x-auto border-b border-outline-variant/15 pb-3 md:block md:overflow-visible md:border-b-0 md:border-r md:pb-0 md:pr-4"
          >
            <button
              type="button"
              aria-current="page"
              className="flex h-9 shrink-0 items-center gap-2 rounded-lg bg-primary px-3 text-xs font-bold text-white shadow-sm shadow-primary/20 md:w-full"
            >
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[16px]"
              >
                tune
              </span>
              General
            </button>
          </nav>

          <section
            aria-labelledby="display-settings-heading"
            className="min-w-0"
          >
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <span
                  aria-hidden="true"
                  className="material-symbols-outlined text-[18px]"
                >
                  palette
                </span>
              </div>
              <div className="min-w-0">
                <h2
                  id="display-settings-heading"
                  className="text-sm font-black text-on-surface"
                >
                  Display
                </h2>
                <p className="mt-0.5 text-[10px] font-bold uppercase tracking-[0.15em] text-outline">
                  Theme and Language
                </p>
              </div>
            </div>

            <DisplayPreferencesControls />
          </section>
        </div>
      </div>
    </main>
  );
}
