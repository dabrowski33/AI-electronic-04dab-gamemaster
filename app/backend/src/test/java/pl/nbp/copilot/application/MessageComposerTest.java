package pl.nbp.copilot.application;

import org.junit.jupiter.api.Test;
import pl.nbp.copilot.model.DecisionCategory;
import pl.nbp.copilot.model.DecisionResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageComposerTest {

    private final MessageComposer composer = new MessageComposer();

    @Test
    void eligibleMessageContainsDisclaimerAndLabel() {
        var result = new DecisionResult(
            DecisionCategory.ELIGIBLE,
            "Uzasadnienie kwalifikacji",
            "Dostarcz sprzęt do serwisu",
            List.of()
        );

        String message = composer.compose(result);

        assertThat(message).contains(MessageComposer.DISCLAIMER);
        assertThat(message).contains("**Decyzja: Kwalifikuje się**");
        assertThat(message).contains("Uzasadnienie kwalifikacji");
    }

    @Test
    void notEligibleMessageContainsDisclaimerAndLabel() {
        var result = new DecisionResult(
            DecisionCategory.NOT_ELIGIBLE,
            "Nie kwalifikuje się z powodu uszkodzenia",
            "Brak możliwości zwrotu",
            List.of()
        );

        String message = composer.compose(result);

        assertThat(message).contains(MessageComposer.DISCLAIMER);
        assertThat(message).contains("**Decyzja: Nie kwalifikuje się**");
        assertThat(message).contains("Nie kwalifikuje się z powodu uszkodzenia");
    }

    @Test
    void needsHumanReviewMessageContainsDisclaimerAndLabel() {
        var result = new DecisionResult(
            DecisionCategory.NEEDS_HUMAN_REVIEW,
            "Wymaga dodatkowej weryfikacji",
            "Skontaktuj się z konsultantem",
            List.of()
        );

        String message = composer.compose(result);

        assertThat(message).contains(MessageComposer.DISCLAIMER);
        assertThat(message).contains("**Decyzja: Wymaga weryfikacji przez konsultanta**");
        assertThat(message).contains("Wymaga dodatkowej weryfikacji");
    }

    @Test
    void moreInfoRequiredMessageContainsDisclaimerAndMissingInfoItems() {
        var result = new DecisionResult(
            DecisionCategory.MORE_INFO_REQUIRED,
            "Potrzebujemy więcej informacji",
            "Prześlij dodatkowe dokumenty",
            List.of("Zdjęcie paragonu", "Zdjęcie uszkodzenia")
        );

        String message = composer.compose(result);

        assertThat(message).contains(MessageComposer.DISCLAIMER);
        assertThat(message).contains("**Decyzja: Wymagane dodatkowe informacje**");
        assertThat(message).contains("Zdjęcie paragonu");
        assertThat(message).contains("Zdjęcie uszkodzenia");
    }
}
