package io.sevcik.hypherator.dto;

/**
 * Result of applying a hyphenation breakpoint to a word.
 *
 * @param left text before the break
 * @param right text after the break
 */
public record HyphenationSplit(String left, String right) {
}
