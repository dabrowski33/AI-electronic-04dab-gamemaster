package pl.nbp.copilot.model;

public enum DecisionCategory {
    ELIGIBLE, NOT_ELIGIBLE, NEEDS_HUMAN_REVIEW, MORE_INFO_REQUIRED;

    public static DecisionCategory fromString(String s) {
        if (s == null || s.isBlank()) {
            return NEEDS_HUMAN_REVIEW;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEEDS_HUMAN_REVIEW;
        }
    }
}
