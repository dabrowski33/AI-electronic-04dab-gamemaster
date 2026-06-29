package pl.nbp.copilot.llm;

import org.springframework.stereotype.Component;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.ImageAnalysis;

@Component
public class PromptCatalog {

    /**
     * JSON schema the vision model MUST follow. The field names and enum values map 1:1 to
     * {@link pl.nbp.copilot.model.ImageAnalysis} and the parser in OpenRouterLlmGateway.
     * Without this explicit contract the model invents its own keys and the analysis comes back empty.
     */
    private static final String IMAGE_ANALYSIS_SCHEMA = """
        Odpowiedz WYŁĄCZNIE obiektem JSON o DOKŁADNIE takich kluczach (nie zmieniaj nazw pól):
        {
          "summary": string,            // krótkie podsumowanie stanu sprzętu po polsku
          "observations": string[],     // lista konkretnych obserwacji po polsku (1-5 pozycji)
          "confidence": "LOW" | "MEDIUM" | "HIGH",   // Twoja pewność oceny
          "signsOfUse": "YES" | "NO" | "UNCERTAIN",      // ślady użytkowania
          "visibleDamage": "YES" | "NO" | "UNCERTAIN",   // widoczne uszkodzenia
          "complete": "YES" | "NO" | "UNCERTAIN",        // czy sprzęt wygląda na kompletny
          "resellableAsNew": "YES" | "NO" | "UNCERTAIN", // czy nadaje się do sprzedaży jako nowy
          "damageType": string | null,  // typ uszkodzenia po polsku lub null gdy brak
          "likelyCause": "MANUFACTURING_DEFECT" | "USER_CAUSED" | "NORMAL_WEAR" | "INCONCLUSIVE"
        }
        Używaj wyłącznie wartości enum podanych powyżej (wielkimi literami, po angielsku).
        Gdy nie możesz czegoś ocenić z pewnością, użyj "UNCERTAIN" (dla pól tri-state) lub "INCONCLUSIVE" (dla likelyCause).
        Nie dodawaj żadnego tekstu poza obiektem JSON.""";

    public String imageAnalysisPrompt(CaseType scenario) {
        String intro = switch (scenario) {
            case ZWROT -> """
                Jesteś asystentem analizy zdjęć dla serwisu obsługi zwrotów sprzętu elektronicznego.
                Przeanalizuj zdjęcie i oceń: ślady użytkowania, widoczne uszkodzenia, kompletność oraz czy
                sprzęt nadaje się do ponownej sprzedaży jako nowy.""";
            case REKLAMACJA -> """
                Jesteś asystentem analizy zdjęć dla serwisu reklamacyjnego sprzętu elektronicznego.
                Przeanalizuj zdjęcie i oceń: czy widoczne jest uszkodzenie i jakiego typu, oraz jaka jest
                najbardziej prawdopodobna przyczyna (wada produkcyjna, uszkodzenie przez użytkownika,
                normalne zużycie czy niejednoznaczna).""";
        };
        return intro + "\n\nUżywaj wyłącznie tego, co faktycznie widać na zdjęciu — nie zgaduj.\n\n" + IMAGE_ANALYSIS_SCHEMA;
    }

    public String decisionPrompt(CaseType scenario, CaseSession session, ImageAnalysis analysis, String policyText) {
        String formData = """
            Typ zgłoszenia: %s
            Kategoria sprzętu: %s
            Model: %s
            Data zakupu: %s
            Opis/powód: %s
            """.formatted(
                session.getType(), session.getCategory(), session.getModel(),
                session.getPurchaseDate(), session.getReason() != null ? session.getReason() : "(brak)"
            );
        String analysisData = """
            Wyniki analizy zdjęcia:
            Podsumowanie: %s
            Obserwacje: %s
            Pewność: %s
            """.formatted(analysis.summary(), String.join(", ", analysis.observations()), analysis.confidence());

        return switch (scenario) {
            case ZWROT -> """
                Jesteś agentem oceniającym zasadność wniosku o zwrot sprzętu elektronicznego.

                DANE FORMULARZA:
                %s

                ANALIZA ZDJĘCIA:
                %s

                POLITYKA ZWROTÓW (stosuj wyłącznie te zasady):
                %s

                Na podstawie powyższych danych oceń zasadność zwrotu.
                Nigdy nie wymyślaj faktów ani zasad spoza podanej polityki.
                Gdy dowody są sprzeczne lub niewystarczające — wybierz NEEDS_HUMAN_REVIEW lub MORE_INFO_REQUIRED.

                %s
                """.formatted(formData, analysisData, policyText, DECISION_SCHEMA);
            case REKLAMACJA -> """
                Jesteś agentem oceniającym zasadność reklamacji sprzętu elektronicznego.

                DANE FORMULARZA:
                %s

                ANALIZA ZDJĘCIA:
                %s

                POLITYKA REKLAMACJI (stosuj wyłącznie te zasady):
                %s

                Na podstawie powyższych danych oceń zasadność reklamacji.
                Nigdy nie wymyślaj faktów ani zasad spoza podanej polityki.
                Gdy dowody są sprzeczne lub niewystarczające — wybierz NEEDS_HUMAN_REVIEW lub MORE_INFO_REQUIRED.

                %s
                """.formatted(formData, analysisData, policyText, DECISION_SCHEMA);
        };
    }

    /**
     * JSON schema the decision model MUST follow. Keys map 1:1 to
     * {@link pl.nbp.copilot.model.DecisionResult} and the parser in OpenRouterLlmGateway.
     */
    private static final String DECISION_SCHEMA = """
        Odpowiedz WYŁĄCZNIE obiektem JSON o DOKŁADNIE takich kluczach (nie zmieniaj nazw pól):
        {
          "category": "ELIGIBLE" | "NOT_ELIGIBLE" | "NEEDS_HUMAN_REVIEW" | "MORE_INFO_REQUIRED",
          "justification": string,   // zwięzłe uzasadnienie decyzji po polsku (2-4 zdania), oparte na danych i polityce
          "nextSteps": string,       // konkretne następne kroki dla klienta po polsku
          "missingInfo": string[]    // gdy MORE_INFO_REQUIRED: lista brakujących informacji po polsku; w innym wypadku []
        }
        Pola "justification" oraz "nextSteps" są OBOWIĄZKOWE i nie mogą być puste.
        Używaj wyłącznie wartości enum podanych dla pola "category" (wielkimi literami, po angielsku).
        Nie dodawaj żadnego tekstu poza obiektem JSON.""";

    public String chatSystemPrompt(CaseSession session) {
        return """
            Jesteś pomocnym asystentem obsługi klienta specjalizującym się w reklamacjach i zwrotach sprzętu elektronicznego.

            Pomagasz klientowi w sprawie: %s (typ: %s, model: %s).
            Podjęta wstępna decyzja: %s.

            Zasady:
            - Odpowiadaj wyłącznie na pytania związane z reklamacją lub zwrotem klienta.
            - Jeśli pytanie jest niezwiązane z tematem, grzecznie odmów i wróć do tematu zgłoszenia.
            - Nie podejmuj wiążących decyzji — Twoja rola jest doradcza.
            - Nie wymyślaj faktów, cen, terminów ani zasad spoza podanych informacji.
            - Odpowiadaj w języku polskim, uprzejmie i zrozumiale.
            """.formatted(
                session.getCategory(), session.getType(), session.getModel(),
                session.getDecision() != null ? session.getDecision().category() : "w toku"
            );
    }
}
