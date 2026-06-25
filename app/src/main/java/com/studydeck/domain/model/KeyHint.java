package com.studydeck.domain.model;

/**
 * Masking helper for AI provider key hints.
 *
 * <p>W1/W3: Uses ONLY {@code java.lang.String} operations — NO {@code javax.crypto}, NO Spring.
 * This keeps it domain-layer clean and ArchUnit-safe.
 *
 * <p>Format rules:
 *
 * <ul>
 *   <li>Key length &ge; 9 chars → {@code first4 + "…" + last4} (e.g. {@code "sk-o…7Xz4"})
 *   <li>Key length &lt; 9 chars → fully masked as {@code "•••••"}
 * </ul>
 */
public final class KeyHint {

  private static final String FULL_MASK = "•••••";
  private static final String SEPARATOR = "…";

  private KeyHint() {}

  /**
   * Computes the masked hint for the given plaintext key.
   *
   * @param plaintextKey the original API key; must not be null
   * @return the masked display hint
   */
  public static String compute(String plaintextKey) {
    if (plaintextKey == null || plaintextKey.length() < 9) {
      return FULL_MASK;
    }
    return plaintextKey.substring(0, 4)
        + SEPARATOR
        + plaintextKey.substring(plaintextKey.length() - 4);
  }
}
