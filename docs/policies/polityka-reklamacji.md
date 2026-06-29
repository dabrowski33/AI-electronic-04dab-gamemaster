# Polityka reklamacji (rękojmia / gwarancja)

> Dokument przykładowy (starter) dla MVP. Wstrzykiwany do promptu agenta w scenariuszu **Reklamacja**.
> Podstawa: rękojmia za wady (odpowiedzialność ustawowa sprzedawcy) oraz gwarancja producenta.
> Treść do zweryfikowania i zastąpienia przez dział prawny/biznes.

## 1. Zakres

Polityka dotyczy **zgłoszeń wad sprzętu** (sprzęt jest uszkodzony lub działa nieprawidłowo). Nie dotyczy zwrotu sprawnego towaru — ten obsługuje `polityka-zwrotow.md`.

## 2. Termin

- Reklamacja z tytułu rękojmi może zostać złożona w okresie odpowiedzialności sprzedawcy, co do zasady **do 2 lat** od wydania towaru.
- Na potrzeby wstępnej oceny w MVP termin liczony jest od **daty zakupu** podanej w formularzu.
- Jeżeli od daty zakupu minęło **więcej niż 2 lata** → **Nie kwalifikuje się** (poza okresem rękojmi), z opcją eskalacji w przypadku gwarancji producenta o dłuższym okresie.

## 3. Co podlega reklamacji

Reklamacja jest zasadna, gdy wada wynika z **przyczyny tkwiącej w towarze** (wada produkcyjna / materiałowa / niezgodność z umową), a nie z działania użytkownika.

### Najczęstsze typy wad kwalifikujących
- Wada produkcyjna / fabryczna (np. niedziałający podzespół bez śladów uszkodzeń zewnętrznych).
- Usterka ujawniona w normalnym użytkowaniu.
- Niezgodność towaru z opisem/umową.

### Typowe przyczyny **niekwalifikujące** (uszkodzenia z winy użytkownika)
- Uszkodzenia mechaniczne (upadek, pęknięcia, wgniecenia, zbita matryca).
- Zalanie / kontakt z cieczą (jeśli sprzęt nie jest do tego przeznaczony).
- Ślady nieautoryzowanej naprawy lub ingerencji.
- Zużycie eksploatacyjne (normalne zużycie materiałów eksploatacyjnych).
- Niewłaściwe użytkowanie niezgodne z instrukcją.

## 4. Reguły decyzji (dla agenta)

Na podstawie analizy zdjęcia (typ uszkodzenia i prawdopodobna przyczyna) oraz opisu z formularza:

| Sytuacja | Decyzja |
|---|---|
| W okresie rękojmi **oraz** analiza wskazuje na wadę produkcyjną / przyczynę tkwiącą w towarze | **Kwalifikuje się** |
| Analiza wskazuje na **uszkodzenie mechaniczne / zalanie / winę użytkownika** | **Nie kwalifikuje się** — z opcją eskalacji |
| Po okresie rękojmi (ponad 2 lata od zakupu) | **Nie kwalifikuje się** (poza terminem) — z opcją eskalacji dla gwarancji |
| Przyczyna wady **niejednoznaczna** na zdjęciu (defekt produkcyjny vs. użytkownik) | **Wymaga weryfikacji przez konsultanta** |
| Zdjęcie/opis niewystarczające, nie widać miejsca usterki | **Wymagane dodatkowe informacje** |

> Zasada nadrzędna: jeśli przyczyna uszkodzenia jest sporna lub dowody są sprzeczne, agent **nie rozstrzyga** definitywnie — kieruje sprawę do **weryfikacji przez konsultanta**.

## 5. Wymagane informacje do rozpatrzenia

- Opis objawów wady (pole „przyczyna" w formularzu — wymagane przy reklamacji).
- Zdjęcie pokazujące wadę / miejsce usterki.
- Model/nazwa sprzętu i data zakupu.

W razie braków → **Wymagane dodatkowe informacje**.

## 6. Następne kroki przy decyzji pozytywnej

1. Rejestracja zgłoszenia reklamacyjnego.
2. Dostarczenie sprzętu do serwisu/sprzedawcy zgodnie z instrukcją.
3. Rozpatrzenie reklamacji i wybór sposobu załatwienia (naprawa, wymiana, obniżenie ceny lub zwrot środków) zgodnie z przepisami.

> Uwaga: ocena jest **wstępna i niewiążąca**; ostateczną decyzję podejmuje konsultant po weryfikacji.
