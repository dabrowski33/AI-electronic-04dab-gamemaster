package pl.nbp.copilot.application;

import org.springframework.stereotype.Component;
import pl.nbp.copilot.model.DecisionCategory;
import pl.nbp.copilot.model.DecisionResult;

@Component
public class MessageComposer {

    static final String DISCLAIMER = "„To wstępna, automatyczna ocena Twojego zgłoszenia, a nie wiążąca decyzja. Ostateczną decyzję podejmuje konsultant po weryfikacji zgłoszenia.”";

    public String compose(DecisionResult result) {
        var sb = new StringBuilder();
        sb.append("Dziękujemy za zgłoszenie. Oto wstępna ocena Twojego przypadku:\n\n");
        sb.append(categoryLabel(result.category())).append("\n\n");
        sb.append(result.justification()).append("\n\n");
        sb.append("**Następne kroki:**\n\n").append(result.nextSteps()).append("\n\n");
        if (result.category() == DecisionCategory.MORE_INFO_REQUIRED
                && result.missingInfo() != null && !result.missingInfo().isEmpty()) {
            sb.append("**Wymagane informacje:**\n\n");
            result.missingInfo().forEach(item -> sb.append("- ").append(item).append("\n"));
            sb.append("\n");
        }
        sb.append(DISCLAIMER);
        return sb.toString();
    }

    private String categoryLabel(DecisionCategory category) {
        return switch (category) {
            case ELIGIBLE -> "**Decyzja: Kwalifikuje się**";
            case NOT_ELIGIBLE -> "**Decyzja: Nie kwalifikuje się**";
            case NEEDS_HUMAN_REVIEW -> "**Decyzja: Wymaga weryfikacji przez konsultanta**";
            case MORE_INFO_REQUIRED -> "**Decyzja: Wymagane dodatkowe informacje**";
        };
    }
}
