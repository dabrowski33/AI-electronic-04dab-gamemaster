# GenRL GameMaster AI
## Kompleksowa Dokumentacja Projektowa: PRD, ARD & PLAN SDLC

Niniejsza dokumentacja techniczna stanowi formalną specyfikację funkcjonalną i architektoniczną dla zaawansowanego systemu neuroewolucyjnego **GenRL GameMaster AI**, przeznaczonego do automatycznej nauki i optymalizacji strategii rozgrywki w środowiskach gier dwuwymiarowych (2D Simulator, np. *Super Mario*, *Pacman*). System łączy techniki uczenia ze wzmocnieniem (Reinforcement Learning) z meta-optymalizacją realizowaną przez algorytmy genetyczne (GA), wykorzystując wieloagentową orkiestrację LLM/LRM.

---

## 1. PRD (Product Requirements Document) – Dokument Wymagań Produktowych

### 1.1. Cel i wizja produktu
Głównym celem systemu **GenRL GameMaster AI** jest stworzenie samouczącego się agenta, który bez uprzedniej znajomości reguł gry potrafi opanować środowisko gry 2D wyłącznie na podstawie stanów końcowych (wygrana/przegrana) oraz sygnałów rzadkich nagród. Kluczową innowacją produktu jest zastosowanie pętli algorytmu genetycznego nadzorowanej przez modele LRM (Large Reasoning Models). Modele te analizują dotychczasowy przebieg ewolucji kodu oraz wag sieci, a następnie modyfikują strukturę kodu algorytmu i hiperparametry uczenia w celu przełamania lokalnych minimów wydajności.

### 1.2. Grupa docelowa (Persony)
* **Badacz AI / Inżynier R&D (Persona: Badacz):** Osoba poszukująca elastycznego środowiska do testowania hybrydowych algorytmów ewolucyjno-wzmocnieniowych. Wymaga pełnego wglądu w logi mutacji, strukturę populacji genomów oraz wykresy zbieżności funkcji przystosowania.
* **Inżynier QA / Tester Automatyczny (Persona: Weryfikator):** Specjalista wykorzystujący autonomicznego agenta do wykrywania błędów (glitchy, niewidzialnych ścian, luk w mapach) w nowo projektowanych grach 2D poprzez masową eksplorację poziomów.

### 1.3. Kluczowe funkcjonalności (Features) i wymagania produktowe
1. **Automatyczny Parser Środowiska i Stanu:** Moduł odczytujący macierze pikseli lub wektory cech z symulatora 2D gier *Super Mario* / *Pacman* i mapujący je na przestrzeń stanów RL.
2. **Orkiestrator Ewolucyjny (LangGraph Loop):** Graf stanów sterujący cyklem życia populacji agentów. Odpowiada za ewaluację pokolenia, selekcję, mutację kodu algorytmicznego oraz krzyżowanie parametrów.
3. **Odizolowany Moduł Wykonawczy (OpenCode Sandbox):** Bezpieczny komponent uruchamiający zmodyfikowany kod Pythona/C++ odpowiedzialny za interakcję agenta z grą.
4. **Kaskada Przetwarzania Kognitywnego (Hermes 3 / Codex):** System inteligentnego generowania zmian w kodzie nagrody (Reward Shaping) i mutacji struktur sieci przy użyciu modelu Hermes 3, z redundantnym przełączeniem awaryjnym na OpenAI Codex.
5. **Instytucjonalny Pulpit Kontrolny (NBP-Style Dashboard):** Wizualna warstwa prezentacji danych oparta na rygorystycznym wzornictwie finansowym, eliminująca elementy rozpraszające na rzecz surowej analizy metryk zbieżności.

### 1.4. Konkretne User Stories (Opowieści Użytkowników)

> **US-01: Inicjalizacja Autonomicznego Treningu w Środowisku 2D**
> * **Jako** Badacz AI,
> * **chcę** wybrać rodzaj symulatora (np. Pacman) z poziomu interfejsu i zdefiniować bazową funkcję celu,
> * **aby** system uruchomił pierwszą generację agentów uczących się zasad gry od zera.
> * **Kryteria akceptacji:** System poprawnie ładuje instancję gry w tle, inicjalizuje bazową macierz Q-learningu lub wagi sieci neuronowej i loguje pierwszy krok epoki w bazie PostgreSQL w czasie krótszym niż `t < 3.0s`.

> **US-02: Modyfikacja Kodu Algorytmu przez Agenta LRM**
> * **Jako** Inżynier R&D,
> * **chcę**, aby w momencie stagnacji wyników (brak wzrostu średniej nagrody przez 50 generacji) model Hermes 3 przeanalizował historię i wygenerował nową mutację algorytmu genetycznego,
> * **Kryteria akceptacji:** System przekazuje dotychczasowy przebieg funkcji przystosowania jako kontekst do OpenRouter (model Hermes 3), otrzymuje spójną semantycznie poprawkę kodu algorytmu i wyświetla pełny proces myślowy (reasoning tokens) w dedykowanym panelu w stylistyce złota NBP.

> **US-03: Bezpieczna Egzekucja Kodu Poprzez OpenCode Worker**
> * **Jako** Administrator Systemu,
> * **chcę**, aby wygenerowany przez LLM dynamiczny kod mutacji algorytmu był uruchamiany przez robotnika OpenCode w izolowanym kontenerze,
> * **Kryteria akceptacji:** Każda próba ucieczki z piaskownicy (np. wywołania systemowe, operacje na plikach hosta) skutkuje przerwaniem wątku, zwróceniem kodu błędu do PostgreSQL i powiadomieniem modułu orkiestracji o konieczności korekty kodu.

> **US-04: Odporność na Błędy API poprzez Kaskadę OpenAI Codex**
> * **Jako** Inżynier DevOps,
> * **chcę**, aby w przypadku niedostępności platformy OpenRouter, zapytania o generowanie nowego kodu były kieorwane do API OpenAI Codex z użyciem zapasowego klucza ChatGPT API,
> * **Kryteria akceptacji:** Przełączenie następuje w sposób przezroczysty dla trwającego eksperymentu ewolucyjnego, a informacja o failoverze jest trwale zapisywana w logu audytowym bazy PostgreSQL.

### 1.5. Wymagania niefunkcjonalne
* **Izolacja środowiska:** Czas życia kontenera OpenCode Worker dla pojedynczej ewaluacji osobnika ograniczony do maksymalnie 60 sekund w celu przeciwdziałania pętlom nieskończonym.
* **Wydajność bazy danych:** Zapis stanów gry i parametrów chromosomów optymalizowany pod kątem przepustowości do 500 wpisów na sekundę przy użyciu mechanizmu unifikacji transakcji (bulk inserts) w PostgreSQL.
* **Zgodność wzorcowa:** Interfejs frontendu musi ściśle implementować paletę kolorystyczną serwisu nbp.pl, zachowując kontrast WCAG na poziomie minimum 4.5:1.

### 1.6. Kryteria sukcesu i kluczowe metryki (KPI)

| Identyfikator | Nazwa Metryki (KPI) | Wartość Docelowa | Metodologia Pomiaru |
| :--- | :--- | :--- | :--- |
| **KPI-01** | Wskaźnik Konwergencji Ewolucyjnej | > 90% przypadków | Osiągnięcie stabilnego poziomu przejścia 1. etapu gry (Super Mario/Pacman) przed 200. generacją algorytmu genetycznego. |
| **KPI-02** | Efektywność Pracy Robotnika (OpenCode Worker) | < 5% odrzuceń syntaktycznych | Stosunek kodu poprawnie skompilowanego/uruchomionego w kontenerze do całkowitej liczby mutacji generowanych przez model LRM. |
| **KPI-03** | Stabilność Kaskady LLM (Failover Latency) | < 4.0 sekundy | Czas migracji kontekstu i uzyskania poprawki z OpenAI Codex po awarii węzła OpenRouter. |

---

## 2. ARD (Architectural Reference Document) – Architektoniczny Dokument Referencyjny

### 2.1. Wielojęzyczna (Poliglotyczna) Architektura Systemu
W celu optymalizacji obciążeń i wykorzystania natywnych ekosystemów technologicznych, system został zaprojektowany w oparciu o architekturę mikrousług skorelowanych funkcjonalnie:

| Warstwa / Komponent | Technologia | Uzasadnienie Wyboru i Rola Architektoniczna |
| :--- | :--- | :--- |
| **Prezentacja i Monitoring** | `React.js / TypeScript` | Odpowiada za rendering siatki symulatora 2D oraz wizualizację drzewa genealogicznego algorytmu genetycznego. Stylistyka i komponenty nawiązują bezpośrednio do serwisu **nbp.pl**. |
| **Orkiestracja i Zarządzanie Stanem AI** | `Python / LangGraph / LangChain` | Rdzeń logiczny systemu. `LangGraph` służy do implementacji stanowej pętli ewolucyjnej. `LangChain` unifikuje dostęp do dostawców OpenRouter i OpenAI. |
| **Integracja Symulatora & Analiza Strukturalna** | `Java / LangChain4j / Spring Boot` | Zapewnia stabilne środowisko do kontroli instancji emulatorów gier. `LangChain4j` służy do strukturalnego mapowania genomów i walidacji typów parametrów przed egzekucją. |
| **BFF & Stream Gateway** | `Node.js / Express / WebSockets` | Odpowiada za niskopoziomowe, asynchroniczne przesyłanie klatek obrazu z symulatora bezpośrednio do przeglądarki użytkownika oraz autoryzację API. |
| **Trwała Warstwa Danych** | `PostgreSQL (v16)` | Relacyjna baza danych zapewniająca pełną zgodność ACID. Przechowuje sekwencje chromosomów, parametry sieci, logi sesji LRM oraz metryki wydajnościowe. |

### 2.2. Topologia Agentowa i Pętla Reasoning-Evolution
Logika podejmowania decyzji i transformacji kodu opiera się na cyklicznym grafie agentowym zarządzanym przez komponent Pythona (`LangGraph`). Architektura wyróżnia następujące role i kroki:
* **Ewaluacja (Java / OpenCode):** Środowisko Java uruchamia symulator 2D. Kod sterujący wykonuje akcje w grze, a wyniki są agregowane i zapisywane w bazie PostgreSQL.
* **Analiza Stagnacji:** Węzeł monitorujący sprawdza trend funkcji przystosowania. Jeśli wykryty zostanie brak postępu, następuje aktywacja węzła wnioskowania (Reasoning Node).
* **Generowanie Mutacji (Hermes 3):** Poprzez platformę **OpenRouter**, model **Hermes 3** otrzymuje aktualny kod algorytmu genetycznego oraz historię prób. Wykorzystując tokeny wnioskowania, dokonuje modyfikacji kodu (np. adaptacja mutacji lub redefinicja nagród). W przypadku awarii sieci następuje automatyczny failover do **OpenAI Codex**.
* **Weryfikacja:** Zmodyfikowany kod trafia do kontenera **OpenCode Worker** w celu egzekucji i wdrożenia nowej generacji.

### 2.3. Standardy Projektowe Identyfikacji Wizualnej (Kanon NBP.pl)
Warstwa prezentacji systemu odrzuca generyczne szablony technologiczne na rzecz autorytarnego wzornictwa nawiązującego do Narodowego Banku Polskiego:
* **Paleta Kolorystyczna:** Kolorem dominującym jest głęboki granat instytucjonalny (`#002C5B`), symbolizujący stabilność i bezpieczeństwo. Akcenty i obramowania wykorzystują stonowane złoto (`#B59A57`). Tło aplikacji przyjmuje odcień matowego kremu (`#FCFBFA`).
* **Typografia i Układ:** Nagłówki główne wykorzystują klasyczną czcionkę szeryfową (*Georgia*). Wszystkie sekcje informacyjne, tabele oraz karty wyników posiadają wyraźne, cienkie linie podziału, nawiązujące do struktury oficjalnych sprawozdań finansowych.

---

## 3. PLAN (Plan Projektowy i Harmonogram SDLC)

Projekt prowadzony będzie w zwinnej metodyce Scrum w ramach 6-miesięcznego, rygorystycznego cyklu życia oprogramowania (SDLC). Każdy sprint trwa dokładnie 2 tygodnie.

### 3.1. Faza 1: Inicjacja, Modelowanie Matematyczne i UX NBP (Miesiąc 1-2)
* Opracowanie kompletnej księgi znaków i makiet w programie Figma z uwzględnieniem surowej palety kolorystycznej i typograficznej NBP.pl.
* Implementacja schematu bazy danych w PostgreSQL (tabele dla chromosomów, logów agentów i stanów środowiska).
* Przygotowanie środowiska Java do uruchamiania gier *Super Mario* oraz *Pacman* v trybie bezgłowym (headless mode).

### 3.2. Faza 2: Faza Wdrożeniowa i Implementacja Komponentów (Miesiąc 3-4)
Implementacja kluczowych domen biznesowych systemu w ramach iteracyjnych przyrostów:

| Etap (Sprinty) | Domena Technologiczna | Zakres Prac i Kamienie Milowe (Milestones) |
| :--- | :--- | :--- |
| **Sprint 1 - 2** | Infrastruktura Podkładowa i Node.js Gateway | Konfiguracja potoków automatyzacji CI/CD (GitHub Actions). Implementacja serwera BFF w Node.js, uruchomienie komunikacji przez WebSockets do przesyłania stanów gier. Implementacja layoutu wizualnego NBP w React.js. |
| **Sprint 3 - 4** | Rdzeń Agentowy AI (Python / LangGraph) | Implementacja grafu stanów ewolucyjnych w LangGraph. Integracja z OpenRouter celem komunikacji z modelem Hermes 3. Opracowanie struktur promptów dla mutacji algorytmu genetycznego. Zapewnienie obsługi tokenów myślowych. |
| **Sprint 5 - 6** | Warstwa Java (LangChain4j) & OpenCode | Uruchomienie mikrousługi Java walidującej strukturalną poprawność kodu. Integracja z piaskownicą robotnika OpenCode. Implementacja mechanizmu przezroczystego przełączania awaryjnego (failover) do OpenAI Codex. |

### 3.3. Faza 3: Testy Zaawansowane, Optymalizacja i Jakość (Miesiąc 5)
* Weryfikacja odporności systemu na zjawisko przedwczesnej zbieżności algorytmu genetycznego (Genetic Drift).
* Przeprowadzenie zaawansowanych testów penetracyjnych izolacji kontenerów pod kątem prób eskalacji uprawnień przez wygenerowany kod autonomiczny.
* Udostępnienie platformy wybranemu zespołowi 500 inżynierów i badaczy w celu oceny czytelności procesu wnioskowania oraz spójności interfejsu NBP-Style.

### 3.4. Faza 4: Wdrożenie Produkcyjne i Utrzymanie (Miesiąc 6)
* Osadzenie systemu na klastrze Kubernetes z pełną konteneryzacją usług Node.js, Python, Java oraz bazy PostgreSQL.
* Konfiguracja systemów Datadog oraz Firebase Performance pod kątem monitorowania opóźnień generowania tokenów ewolucyjnych oraz utraty połączeń z API zewnętrznymi.
* Przekazanie systemu do eksploatacji, konfiguracja automatycznych kopii zapasowych baz PostgreSQL zawierających wyewoluowane, optymalne genomy agentów.
