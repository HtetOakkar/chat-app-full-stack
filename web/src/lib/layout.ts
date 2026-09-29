/**
 * Matches Tailwind's `md` breakpoint, where the dashboard switches from the
 * mobile master-detail stack to the wide multi-column layout (ADR-0006).
 */
export const WIDE_LAYOUT_QUERY = "(min-width: 768px)";

/**
 * Whether the viewport currently renders the wide layout. Environments
 * without `matchMedia` (SSR, some test runners) are treated as wide.
 */
export function isWideLayout(): boolean {
  if (
    typeof window === "undefined" ||
    typeof window.matchMedia !== "function"
  ) {
    return true;
  }
  return window.matchMedia(WIDE_LAYOUT_QUERY).matches;
}
