package io.sevcik.hypherator.dto;

/**
 * Public representation of a hyphenation breakpoint in a logical word.
 *
 * @param logicalOffset offset in the original word, measured in UTF-16 code units
 * @param priority raw breakpoint priority from the pattern matcher
 * @param replacement replacement rule payload, or null when the breakpoint is a plain split
 * @param replacementIndex replacement rule start index as defined by the dictionary format
 * @param replacementCount replacement rule count as defined by the dictionary format
 */
public record HyphenationCandidate(
        int logicalOffset,
        int priority,
        String replacement,
        int replacementIndex,
        int replacementCount
) {

    public boolean hasReplacement() {
        return replacement != null;
    }
}
