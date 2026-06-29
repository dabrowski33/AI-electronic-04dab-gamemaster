package pl.nbp.copilot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionCategoryTest {

    @Test
    void fromStringEligible() {
        assertThat(DecisionCategory.fromString("ELIGIBLE")).isEqualTo(DecisionCategory.ELIGIBLE);
    }

    @Test
    void fromStringUnknownFallsBackToNeedsHumanReview() {
        assertThat(DecisionCategory.fromString("UNKNOWN")).isEqualTo(DecisionCategory.NEEDS_HUMAN_REVIEW);
    }

    @Test
    void fromStringEmptyFallsBackToNeedsHumanReview() {
        assertThat(DecisionCategory.fromString("")).isEqualTo(DecisionCategory.NEEDS_HUMAN_REVIEW);
    }

    @Test
    void fromStringNullFallsBackToNeedsHumanReview() {
        assertThat(DecisionCategory.fromString(null)).isEqualTo(DecisionCategory.NEEDS_HUMAN_REVIEW);
    }
}
