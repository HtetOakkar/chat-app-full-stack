import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { hydrateRoot } from "react-dom/client";
import { renderToString } from "react-dom/server";
import {
  DisplayPreferencesProvider,
  displayPreferencesStorageKey,
  useDisplayPreferences,
} from "./DisplayPreferencesContext";
import DisplayPreferencesControls from "@/components/DisplayPreferencesControls";
import { useTranslations } from "@/lib/i18n";

let prefersDark = false;
let mediaListener: ((event: MediaQueryListEvent) => void) | null = null;

const installMatchMedia = () => {
  window.matchMedia = jest.fn().mockImplementation((query) => ({
    media: query,
    matches: prefersDark,
    addEventListener: (_event: string, listener: typeof mediaListener) => {
      mediaListener = listener;
    },
    removeEventListener: jest.fn(),
  }));
};

function Probe() {
  const { appearance, effectiveAppearance, language } = useDisplayPreferences();
  return (
    <div>
      <DisplayPreferencesControls />
      <div data-testid="appearance">{appearance}</div>
      <div data-testid="effective">{effectiveAppearance}</div>
      <div data-testid="language">{language}</div>
    </div>
  );
}

function LoadingTextProbe() {
  const t = useTranslations();
  return <span>{t.loadingApp}</span>;
}

describe("DisplayPreferencesProvider", () => {
  beforeEach(() => {
    prefersDark = false;
    mediaListener = null;
    window.localStorage.clear();
    document.documentElement.removeAttribute("data-appearance");
    document.documentElement.removeAttribute("data-appearance-preference");
    document.documentElement.removeAttribute("data-interface-language");
    document.documentElement.classList.remove("dark");
    installMatchMedia();
  });

  it("defaults to system appearance, English, and follows system changes", () => {
    render(
      <DisplayPreferencesProvider>
        <Probe />
      </DisplayPreferencesProvider>
    );

    expect(screen.getByTestId("appearance")).toHaveTextContent("system");
    expect(screen.getByTestId("effective")).toHaveTextContent("light");
    expect(screen.getByTestId("language")).toHaveTextContent("en");
    expect(document.documentElement.dataset.appearance).toBe("light");

    act(() => {
      prefersDark = true;
      mediaListener?.({ matches: true } as MediaQueryListEvent);
    });

    expect(screen.getByTestId("effective")).toHaveTextContent("dark");
    expect(document.documentElement.dataset.appearance).toBe("dark");
    expect(document.documentElement).toHaveClass("dark");
  });

  it("persists explicit appearance and language selections", () => {
    render(
      <DisplayPreferencesProvider>
        <Probe />
      </DisplayPreferencesProvider>
    );

    fireEvent.change(screen.getByLabelText(/appearance/i), {
      target: { value: "dark" },
    });
    fireEvent.change(screen.getByLabelText(/interface language/i), {
      target: { value: "my" },
    });

    expect(screen.getByTestId("appearance")).toHaveTextContent("dark");
    expect(screen.getByTestId("language")).toHaveTextContent("my");
    expect(document.documentElement.dataset.appearance).toBe("dark");
    expect(window.localStorage.getItem(displayPreferencesStorageKey)).toContain(
      '"language":"my"'
    );
  });

  it("restores persisted Light preference on reload", () => {
    window.localStorage.setItem(
      displayPreferencesStorageKey,
      JSON.stringify({ appearance: "light", language: "my" })
    );

    render(
      <DisplayPreferencesProvider>
        <Probe />
      </DisplayPreferencesProvider>
    );

    expect(screen.getByTestId("appearance")).toHaveTextContent("light");
    expect(screen.getByTestId("effective")).toHaveTextContent("light");
    expect(screen.getByTestId("language")).toHaveTextContent("my");
  });

  it("hydrates with default text before restoring browser preferences", async () => {
    window.localStorage.setItem(
      displayPreferencesStorageKey,
      JSON.stringify({ appearance: "dark", language: "my" })
    );
    const App = () => (
      <DisplayPreferencesProvider>
        <LoadingTextProbe />
      </DisplayPreferencesProvider>
    );
    const originalWindow = global.window;
    // Simulate Next server rendering, where browser storage is unavailable.
    Reflect.deleteProperty(global, "window");
    const serverHtml = renderToString(<App />);
    global.window = originalWindow;
    expect(serverHtml).toContain("Opening Meow Chit Chat...");
    const container = document.createElement("div");
    container.innerHTML = serverHtml;
    document.body.appendChild(container);
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => {});

    await act(async () => {
      hydrateRoot(container, <App />);
    });

    expect(consoleError).not.toHaveBeenCalledWith(
      expect.stringContaining("Hydration failed"),
      expect.anything()
    );
    expect(
      await screen.findByText("Meow Chit Chat ဖွင့်နေသည်...")
    ).toBeInTheDocument();

    consoleError.mockRestore();
    document.body.removeChild(container);
  });
});
