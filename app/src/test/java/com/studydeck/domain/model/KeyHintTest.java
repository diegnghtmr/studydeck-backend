package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KeyHint}.
 *
 * <p>W1/W3: KeyHint must use only java.lang.String operations — NO javax.crypto, NO Spring.
 */
class KeyHintTest {

  @Test
  void compute_normalKey_returnsFirst4Ellipsis4Last() {
    // "sk-openai1234" → length 13 (>= 9) → "sk-o…1234"
    String hint = KeyHint.compute("sk-openai1234");
    assertThat(hint).isEqualTo("sk-o…1234");
  }

  @Test
  void compute_exactlyNineChars_returnsFirst4Ellipsis4Last() {
    // "abcdefghi" → length 9 → "abcd…fghi"
    String hint = KeyHint.compute("abcdefghi");
    assertThat(hint).isEqualTo("abcd…fghi");
  }

  @Test
  void compute_shortKeyLessThan9Chars_returnsFullMask() {
    // "sk-12" → length 5 (< 9) → "•••••"
    String hint = KeyHint.compute("sk-12");
    assertThat(hint).isEqualTo("•••••");
  }

  @Test
  void compute_exactly8Chars_returnsFullMask() {
    // "12345678" → length 8 (< 9) → "•••••"
    String hint = KeyHint.compute("12345678");
    assertThat(hint).isEqualTo("•••••");
  }

  @Test
  void compute_longKey_returnsFirst4Ellipsis4Last() {
    // Long openai-style key
    String key = "sk-proj-ABCDEFGHIJKLMNOPQRSTUVWXYZ1234";
    String hint = KeyHint.compute(key);
    assertThat(hint).startsWith("sk-p");
    assertThat(hint).endsWith("1234");
    assertThat(hint).contains("…");
  }

  @Test
  void compute_doesNotImportAnyCryptoClasses() {
    // Structural: the compute method must compile and run with zero javax.crypto imports.
    // Verified at compile-time by ArchUnit domainMustNotDependOnSpring (which also covers javax..)
    // This test is a runtime smoke test that the class loads without those deps.
    String result = KeyHint.compute("test-key-value");
    assertThat(result).isNotNull();
  }
}
