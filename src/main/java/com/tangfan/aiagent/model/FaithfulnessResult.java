package com.tangfan.aiagent.model;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public final class FaithfulnessResult {
    private final List<Claim> claims;
    private final OptionalDouble faithfulnessScore;
    private final Status status;

    public FaithfulnessResult(List<Claim> claims, OptionalDouble faithfulnessScore, Status status) {
        this.claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        this.faithfulnessScore = Objects.requireNonNull(faithfulnessScore, "faithfulnessScore");
        this.status = Objects.requireNonNull(status, "status");

        if (faithfulnessScore.isPresent()
                && (!Double.isFinite(faithfulnessScore.getAsDouble())
                || faithfulnessScore.getAsDouble() < 0.0
                || faithfulnessScore.getAsDouble() > 1.0)) {
            throw new IllegalArgumentException("Faithfulness score must be between 0 and 1");
        }
        if ((status == Status.CHECKED) != faithfulnessScore.isPresent()) {
            throw new IllegalArgumentException("Only checked results must have a faithfulness score");
        }
    }

    public List<Claim> claims() {
        return claims;
    }

    public OptionalDouble faithfulnessScore() {
        return faithfulnessScore;
    }

    public Status status() {
        return status;
    }

    public static FaithfulnessResult notApplicable() {
        return new FaithfulnessResult(List.of(), OptionalDouble.empty(), Status.NOT_APPLICABLE);
    }

    public enum Status {
        CHECKED,
        NO_EVIDENCE,
        NO_CLAIMS,
        NOT_APPLICABLE
    }

    public static final class Claim {
        private final String text;
        private final boolean supported;
        private final List<Integer> sourceChunkIds;

        public Claim(String text, boolean supported, List<Integer> sourceChunkIds) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Claim text must not be blank");
            }
            this.text = text;
            this.supported = supported;
            this.sourceChunkIds = List.copyOf(
                    Objects.requireNonNull(sourceChunkIds, "sourceChunkIds"));
            if (!supported && !this.sourceChunkIds.isEmpty()) {
                throw new IllegalArgumentException("Unsupported claims must not have source chunks");
            }
        }

        public String text() {
            return text;
        }

        public boolean supported() {
            return supported;
        }

        public List<Integer> sourceChunkIds() {
            return sourceChunkIds;
        }
    }
}
