/**
 * Utility helper to extract and parse email tokens from comma, space, or newline-separated input.
 */
export const parseEmailTokens = (input: string): string[] => {
  return input
    .split(/[\s,;]+/)
    .map((token) => token.trim().toLowerCase())
    .filter((token) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(token));
};
