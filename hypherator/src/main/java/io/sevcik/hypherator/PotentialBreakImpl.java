package io.sevcik.hypherator;

import io.sevcik.hypherator.dto.HyphenationCandidateKind;
import io.sevcik.hypherator.dto.PotentialBreak;

record PotentialBreakImpl(int position, int priority, HyphenDict.BreakRule breakRule,
                          HyphenationCandidateKind kind) implements PotentialBreak {}
