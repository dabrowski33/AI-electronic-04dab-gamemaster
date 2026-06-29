# Session Persistence (H2) + Restore-on-Reload — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a case (form data, image analysis, decision, full chat transcript) durable so reopening `/chat/{sessionId}` — on reload, deep-link, or after a backend restart — restores the conversation exactly where the customer left off.

**Architecture:** Swap the in-memory `SessionStore` for a durable **H2 file-backed** `JpaSessionStore` behind the existing `SessionStore` interface (orchestration code unchanged). Fix two backend correctness gaps the in-memory store hid (assistant chat replies never persisted; chat context built from a stale session object). Wire the Angular chat screen to restore from `GET /api/v1/cases/{id}` when navigation state is absent. Spec: [`docs/ADR/004-persistence.md`](../ADR/004-persistence.md).

**Tech Stack:** Java 21, Spring Boot 3.5.11, Spring Data JPA, H2 (file-backed), Hibernate JSON `AttributeConverter`, JUnit 5 + `@DataJpaTest` + MockMvc, Angular (standalone, signals), Playwright.

## Global Constraints

- **Engine is H2, never SQLite** (ADR-004 §6). No SQLite dependency, no `load_extension`.
- **Image bytes are NOT persisted** (ADR-004 §4.5). No BLOB column, no file written for the photo.
- **Retention: keep indefinitely** — no TTL/purge job (ADR-004 §4.4).
- **`SessionStore` interface is unchanged**; orchestration (`CaseService`) must not change behavior.
- **Full-context tests run under `@ActiveProfiles({"test","stub-llm"})`** → they use `InMemorySessionStore` + `StubLlmGateway`. Production/dev (no profile) uses `JpaSessionStore` + `OpenRouterLlmGateway`.
- **Tests must not write the real DB file** — the `test` profile overrides the datasource to in-memory H2.
- **All user-facing text in Polish** (PRD AC-25).
- **TDD**: write the failing test first, watch it fail, implement minimally, watch it pass, commit.
- **Build/run with Java 21**: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; backend tests via `sh ./mvnw test` from `app/backend/`.

---

### Task 1: Add JPA + H2 dependencies, datasource config, JSON converters

**Files:**
- Modify: `app/backend/pom.xml` (add two dependencies)
- Modify: `app/backend/src/main/resources/application.yaml` (datasource + jpa block)
- Create: `app/backend/src/test/resources/application-test.yaml` (in-memory DB override)
- Create: `app/backend/.gitignore` (ignore the data dir)
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/ImageAnalysisConverter.java`
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/DecisionResultConverter.java`
- Test: `app/backend/src/test/java/pl/nbp/copilot/session/jpa/JsonConverterTest.java`

**Interfaces:**
- Consumes: existing records `pl.nbp.copilot.model.ImageAnalysis`, `pl.nbp.copilot.model.DecisionResult`.
- Produces: `ImageAnalysisConverter implements AttributeConverter<ImageAnalysis,String>` and `DecisionResultConverter implements AttributeConverter<DecisionResult,String>` (used by entity fields in Task 2).

- [ ] **Step 1: Write the failing converter round-trip test**

`app/backend/src/test/java/pl/nbp/copilot/session/jpa/JsonConverterTest.java`:
```java
package pl.nbp.copilot.session.jpa;

import org.junit.jupiter.api.Test;
import pl.nbp.copilot.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConverterTest {

    @Test
    void imageAnalysisRoundTrips() {
        var conv = new ImageAnalysisConverter();
        var original = new ImageAnalysis(
                "opis", List.of("a", "b"), Confidence.HIGH,
                TriState.NO, TriState.NO, TriState.YES, TriState.YES,
                "zarysowanie", LikelyCause.USER_CAUSED);

        var restored = conv.convertToEntityAttribute(conv.convertToDatabaseColumn(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void decisionResultRoundTrips() {
        var conv = new DecisionResultConverter();
        var original = new DecisionResult(
                DecisionCategory.MORE_INFO_REQUIRED, "uzasadnienie", "kroki",
                List.of("brakuje zdjęcia"));

        var restored = conv.convertToEntityAttribute(conv.convertToDatabaseColumn(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void nullsAreHandled() {
        assertThat(new ImageAnalysisConverter().convertToDatabaseColumn(null)).isNull();
        assertThat(new DecisionResultConverter().convertToEntityAttribute(null)).isNull();
    }
}
```
> Note: `ImageAnalysis` and `DecisionResult` are Java records, so `equals()` is value-based — the round-trip assertions are meaningful. The Spring Boot compiler passes `-parameters`, so Jackson deserializes records by constructor parameter names.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=JsonConverterTest`
Expected: FAIL — `ImageAnalysisConverter` / `DecisionResultConverter` do not exist (compilation error).

- [ ] **Step 3: Add dependencies to `pom.xml`**

Insert after the validation starter (around line 42), before the openai-java dependency:
```xml
        <!-- Persistence: durable session/audit store (ADR-004) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 4: Implement the two converters**

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/ImageAnalysisConverter.java`:
```java
package pl.nbp.copilot.session.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pl.nbp.copilot.model.ImageAnalysis;

/** Stores the ImageAnalysis value object as a JSON string column (ADR-004 §4.2). */
@Converter
public class ImageAnalysisConverter implements AttributeConverter<ImageAnalysis, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ImageAnalysis attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ImageAnalysis", e);
        }
    }

    @Override
    public ImageAnalysis convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return MAPPER.readValue(dbData, ImageAnalysis.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ImageAnalysis", e);
        }
    }
}
```

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/DecisionResultConverter.java`:
```java
package pl.nbp.copilot.session.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pl.nbp.copilot.model.DecisionResult;

/** Stores the DecisionResult value object as a JSON string column (ADR-004 §4.2). */
@Converter
public class DecisionResultConverter implements AttributeConverter<DecisionResult, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(DecisionResult attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DecisionResult", e);
        }
    }

    @Override
    public DecisionResult convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return MAPPER.readValue(dbData, DecisionResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize DecisionResult", e);
        }
    }
}
```

- [ ] **Step 5: Add the datasource + JPA block to `application.yaml`**

Append under the existing top-level `spring:` key (it currently only has `servlet.multipart`). The result for `spring:` should read:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB
  datasource:
    url: jdbc:h2:file:./data/copilot;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate.format_sql: false
```
> `AUTO_SERVER=TRUE` lets a second connection (e.g. the H2 console or a DB tool) open the same file while the app runs. `open-in-view: false` avoids holding a JPA session open across the SSE worker thread.

- [ ] **Step 6: Create the test-profile datasource override**

`app/backend/src/test/resources/application-test.yaml`:
```yaml
# The `test` profile keeps the DB in-memory so the suite never writes the real ./data/copilot file.
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
```

- [ ] **Step 7: Ignore the data directory**

`app/backend/.gitignore`:
```gitignore
/data/
*.mv.db
*.trace.db
```

- [ ] **Step 8: Run the converter test to verify it passes**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=JsonConverterTest`
Expected: PASS (3 tests).

- [ ] **Step 9: Commit**

```bash
git add app/backend/pom.xml app/backend/src/main/resources/application.yaml \
        app/backend/src/test/resources/application-test.yaml app/backend/.gitignore \
        app/backend/src/main/java/pl/nbp/copilot/session/jpa/ \
        app/backend/src/test/java/pl/nbp/copilot/session/jpa/JsonConverterTest.java
git commit -m "Backend: add JPA + H2 deps, datasource config, and JSON value-object converters"
```

---

### Task 2: JPA entities, repository, and `JpaSessionStore`

**Files:**
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/CaseSessionEntity.java`
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/ChatMessageEntity.java`
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/CaseSessionRepository.java`
- Create: `app/backend/src/main/java/pl/nbp/copilot/session/jpa/JpaSessionStore.java`
- Modify: `app/backend/src/main/java/pl/nbp/copilot/session/InMemorySessionStore.java` (add `@Profile("test")`)
- Test: `app/backend/src/test/java/pl/nbp/copilot/session/jpa/JpaSessionStoreTest.java`

**Interfaces:**
- Consumes: `SessionStore` (interface: `create(CaseSession)`, `Optional<CaseSession> get(String)`, `appendMessage(String, ChatMessage)`, `boolean exists(String)`), domain `CaseSession`/`ChatMessage`, converters from Task 1.
- Produces: `JpaSessionStore` (default `SessionStore` bean, `@Profile("!test")`). `CaseSessionRepository extends JpaRepository<CaseSessionEntity, String>`.

- [ ] **Step 1: Write the failing persistence test**

`app/backend/src/test/java/pl/nbp/copilot/session/jpa/JpaSessionStoreTest.java`:
```java
package pl.nbp.copilot.session.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import pl.nbp.copilot.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaSessionStore.class)
class JpaSessionStoreTest {

    @Autowired
    JpaSessionStore store;

    private CaseSession sampleSession(String id) {
        var s = new CaseSession(id, CaseType.REKLAMACJA, EquipmentCategory.LAPTOPY_I_KOMPUTERY,
                "Dell XPS 13", LocalDate.of(2026, 6, 1), "Nie włącza się");
        s.setImageAnalysis(new ImageAnalysis("opis", List.of("obs"), Confidence.MEDIUM,
                TriState.UNCERTAIN, TriState.YES, TriState.UNCERTAIN, TriState.NO,
                "pęknięcie", LikelyCause.MANUFACTURING_DEFECT));
        s.setDecision(new DecisionResult(DecisionCategory.NEEDS_HUMAN_REVIEW, "uzasadnienie", "kroki", List.of()));
        s.getMessages().add(new ChatMessage("assistant", "Pierwsza wiadomość", Instant.now()));
        return s;
    }

    @Test
    void createThenGetRoundTripsAllFields() {
        store.create(sampleSession("sess-1"));

        var loaded = store.get("sess-1").orElseThrow();
        assertThat(loaded.getType()).isEqualTo(CaseType.REKLAMACJA);
        assertThat(loaded.getCategory()).isEqualTo(EquipmentCategory.LAPTOPY_I_KOMPUTERY);
        assertThat(loaded.getModel()).isEqualTo("Dell XPS 13");
        assertThat(loaded.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(loaded.getReason()).isEqualTo("Nie włącza się");
        assertThat(loaded.getImageAnalysis().likelyCause()).isEqualTo(LikelyCause.MANUFACTURING_DEFECT);
        assertThat(loaded.getDecision().category()).isEqualTo(DecisionCategory.NEEDS_HUMAN_REVIEW);
        assertThat(loaded.getMessages()).extracting(ChatMessage::content).containsExactly("Pierwsza wiadomość");
    }

    @Test
    void appendMessagePreservesOrder() {
        store.create(sampleSession("sess-2"));
        store.appendMessage("sess-2", new ChatMessage("user", "Pytanie?", Instant.now()));
        store.appendMessage("sess-2", new ChatMessage("assistant", "Odpowiedź.", Instant.now()));

        var loaded = store.get("sess-2").orElseThrow();
        assertThat(loaded.getMessages()).extracting(ChatMessage::role)
                .containsExactly("assistant", "user", "assistant");
        assertThat(loaded.getMessages()).extracting(ChatMessage::content)
                .containsExactly("Pierwsza wiadomość", "Pytanie?", "Odpowiedź.");
    }

    @Test
    void existsAndMissing() {
        assertThat(store.exists("nope")).isFalse();
        assertThat(store.get("nope")).isEmpty();
        store.create(sampleSession("sess-3"));
        assertThat(store.exists("sess-3")).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=JpaSessionStoreTest`
Expected: FAIL — `JpaSessionStore`, `CaseSessionEntity`, etc. do not exist (compilation error).

- [ ] **Step 3: Create the entities**

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/ChatMessageEntity.java`:
```java
package pl.nbp.copilot.session.jpa;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessageEntity() { }

    public ChatMessageEntity(String role, String content, Instant createdAt) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/CaseSessionEntity.java`:
```java
package pl.nbp.copilot.session.jpa;

import jakarta.persistence.*;
import pl.nbp.copilot.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "case_session")
public class CaseSessionEntity {

    @Id
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentCategory category;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(length = 2000)
    private String reason;

    @Convert(converter = ImageAnalysisConverter.class)
    @Lob
    private ImageAnalysis imageAnalysis;

    @Convert(converter = DecisionResultConverter.class)
    @Lob
    private DecisionResult decision;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id")
    @OrderColumn(name = "position")
    private List<ChatMessageEntity> messages = new ArrayList<>();

    protected CaseSessionEntity() { }

    public CaseSessionEntity(String sessionId, CaseType type, EquipmentCategory category, String model,
                             LocalDate purchaseDate, String reason, Instant createdAt) {
        this.sessionId = sessionId;
        this.type = type;
        this.category = category;
        this.model = model;
        this.purchaseDate = purchaseDate;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public CaseType getType() { return type; }
    public EquipmentCategory getCategory() { return category; }
    public String getModel() { return model; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public String getReason() { return reason; }
    public ImageAnalysis getImageAnalysis() { return imageAnalysis; }
    public void setImageAnalysis(ImageAnalysis imageAnalysis) { this.imageAnalysis = imageAnalysis; }
    public DecisionResult getDecision() { return decision; }
    public void setDecision(DecisionResult decision) { this.decision = decision; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ChatMessageEntity> getMessages() { return messages; }
}
```

- [ ] **Step 4: Create the repository**

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/CaseSessionRepository.java`:
```java
package pl.nbp.copilot.session.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseSessionRepository extends JpaRepository<CaseSessionEntity, String> {
}
```

- [ ] **Step 5: Create `JpaSessionStore` (with domain ↔ entity mapping)**

`app/backend/src/main/java/pl/nbp/copilot/session/jpa/JpaSessionStore.java`:
```java
package pl.nbp.copilot.session.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.ChatMessage;
import pl.nbp.copilot.session.SessionStore;

import java.util.Optional;

/** Durable, H2-backed SessionStore (ADR-004). Default outside the `test` profile. */
@Component
@Profile("!test")
public class JpaSessionStore implements SessionStore {

    private final CaseSessionRepository repository;

    public JpaSessionStore(CaseSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void create(CaseSession session) {
        var entity = new CaseSessionEntity(
                session.getSessionId(), session.getType(), session.getCategory(),
                session.getModel(), session.getPurchaseDate(), session.getReason(),
                session.getCreatedAt());
        entity.setImageAnalysis(session.getImageAnalysis());
        entity.setDecision(session.getDecision());
        for (ChatMessage m : session.getMessages()) {
            entity.getMessages().add(new ChatMessageEntity(m.role(), m.content(), m.createdAt()));
        }
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseSession> get(String sessionId) {
        return repository.findById(sessionId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void appendMessage(String sessionId, ChatMessage message) {
        repository.findById(sessionId).ifPresent(entity -> {
            entity.getMessages().add(new ChatMessageEntity(message.role(), message.content(), message.createdAt()));
            repository.save(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String sessionId) {
        return repository.existsById(sessionId);
    }

    private CaseSession toDomain(CaseSessionEntity e) {
        var s = new CaseSession(e.getSessionId(), e.getType(), e.getCategory(),
                e.getModel(), e.getPurchaseDate(), e.getReason());
        s.setImageAnalysis(e.getImageAnalysis());
        s.setDecision(e.getDecision());
        for (ChatMessageEntity m : e.getMessages()) {
            s.getMessages().add(new ChatMessage(m.getRole(), m.getContent(), m.getCreatedAt()));
        }
        return s;
    }
}
```
> The mapping fully materializes `messages` inside the transactional `get`, so the returned domain `CaseSession` is safe to read from the SSE worker thread (no lazy-loading). The domain `CaseSession.createdAt` is reset on map — acceptable, it is not surfaced in any response.

- [ ] **Step 6: Gate `InMemorySessionStore` to the `test` profile**

In `app/backend/src/main/java/pl/nbp/copilot/session/InMemorySessionStore.java`, add the import and annotation:
```java
import org.springframework.context.annotation.Profile;
```
and annotate the class:
```java
@Component
@Profile("test")
public class InMemorySessionStore implements SessionStore {
```
> Full-context tests use `@ActiveProfiles({"test","stub-llm"})` → `InMemorySessionStore` active, `JpaSessionStore` (`!test`) inactive. Dev/prod (no profile) → `JpaSessionStore` active. Exactly one `SessionStore` bean in every context.

- [ ] **Step 7: Run the persistence test to verify it passes**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=JpaSessionStoreTest`
Expected: PASS (3 tests).

- [ ] **Step 8: Run the full backend suite (catch context-load regressions)**

Run: `cd app/backend && sh ./mvnw -q test`
Expected: PASS. (If a `@SpringBootTest` fails to find a `SessionStore` bean, confirm it declares `@ActiveProfiles({"test","stub-llm"})`; all current ones do.)

- [ ] **Step 9: Commit**

```bash
git add app/backend/src/main/java/pl/nbp/copilot/session/
git add app/backend/src/test/java/pl/nbp/copilot/session/jpa/JpaSessionStoreTest.java
git commit -m "Backend: durable H2 JpaSessionStore behind the SessionStore seam (ADR-004)"
```

---

### Task 3: Persist the assistant chat reply + build chat context from the up-to-date session

**Files:**
- Modify: `app/backend/src/main/java/pl/nbp/copilot/llm/LlmGateway.java` (add completion callback to `streamChat`)
- Modify: `app/backend/src/main/java/pl/nbp/copilot/llm/OpenRouterLlmGateway.java` (accumulate deltas; invoke callback on success)
- Modify: `app/backend/src/main/java/pl/nbp/copilot/llm/StubLlmGateway.java` (accumulate tokens; invoke callback on success)
- Modify: `app/backend/src/main/java/pl/nbp/copilot/application/ChatService.java` (reload session after appending user msg; pass callback that persists assistant msg)
- Test: `app/backend/src/test/java/pl/nbp/copilot/application/ChatServiceTest.java`

**Interfaces:**
- Consumes: `SessionStore`, `ChatMessage`, `CaseSession`.
- Produces: new `LlmGateway.streamChat(CaseSession session, String userMessage, SseEmitter emitter, java.util.function.Consumer<String> onAssistantComplete)` — the gateway calls `onAssistantComplete.accept(fullText)` exactly once, only on a successful stream, before completing the emitter.

- [ ] **Step 1: Write the failing `ChatService` test**

`app/backend/src/test/java/pl/nbp/copilot/application/ChatServiceTest.java`:
```java
package pl.nbp.copilot.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.llm.LlmGateway;
import pl.nbp.copilot.model.*;
import pl.nbp.copilot.session.InMemorySessionStore;

import java.time.LocalDate;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    private CaseSession seed(InMemorySessionStore store) {
        var s = new CaseSession("s1", CaseType.ZWROT, EquipmentCategory.TABLETY,
                "iPad", LocalDate.of(2026, 6, 1), null);
        store.create(s);
        return s;
    }

    @Test
    void persistsUserThenAssistantMessageInOrder() {
        var store = new InMemorySessionStore();
        seed(store);

        // Fake gateway: streams two tokens, then reports the full assistant text via the callback.
        LlmGateway gateway = new LlmGateway() {
            public ImageAnalysis analyzeImage(CaseType s, byte[] b) { return null; }
            public DecisionResult decide(CaseType s, CaseSession c, String p) { return null; }
            public void streamChat(CaseSession session, String userMessage, SseEmitter emitter,
                                   Consumer<String> onAssistantComplete) {
                // The user message must already be in the session the gateway sees.
                assertThat(session.getMessages()).extracting(ChatMessage::content).contains("Czy mogę zwrócić?");
                onAssistantComplete.accept("Tak, można.");
            }
        };

        var service = new ChatService(gateway, store);
        service.streamChat("s1", "Czy mogę zwrócić?", new SseEmitter());

        var saved = store.get("s1").orElseThrow();
        assertThat(saved.getMessages()).extracting(ChatMessage::role).containsExactly("user", "assistant");
        assertThat(saved.getMessages()).extracting(ChatMessage::content)
                .containsExactly("Czy mogę zwrócić?", "Tak, można.");
    }

    @Test
    void doesNotPersistAssistantWhenStreamFails() {
        var store = new InMemorySessionStore();
        seed(store);
        LlmGateway gateway = new LlmGateway() {
            public ImageAnalysis analyzeImage(CaseType s, byte[] b) { return null; }
            public DecisionResult decide(CaseType s, CaseSession c, String p) { return null; }
            public void streamChat(CaseSession session, String userMessage, SseEmitter emitter,
                                   Consumer<String> onAssistantComplete) {
                throw new RuntimeException("stream blew up"); // callback never invoked
            }
        };
        var service = new ChatService(gateway, store);
        service.streamChat("s1", "Pytanie?", new SseEmitter());

        var saved = store.get("s1").orElseThrow();
        assertThat(saved.getMessages()).extracting(ChatMessage::role).containsExactly("user");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=ChatServiceTest`
Expected: FAIL — `LlmGateway.streamChat` does not take a `Consumer` argument (compilation error).

- [ ] **Step 3: Extend the `LlmGateway` interface**

`app/backend/src/main/java/pl/nbp/copilot/llm/LlmGateway.java` — replace the `streamChat` line:
```java
import java.util.function.Consumer;
// ...
    void streamChat(CaseSession session, String userMessage, SseEmitter emitter,
                    Consumer<String> onAssistantComplete);
```

- [ ] **Step 4: Update `OpenRouterLlmGateway.streamChat` to accumulate and report**

Replace the method body (lines ~130-163) so it builds the full text and calls the callback only on success:
```java
    @Override
    public void streamChat(CaseSession session, String userMessage, SseEmitter emitter,
                           java.util.function.Consumer<String> onAssistantComplete) {
        ChatCompletionCreateParams request = buildChatRequest(session);
        StringBuilder full = new StringBuilder();

        try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(request)) {
            stream.stream().forEach(chunk -> {
                String delta = chunk.choices().stream()
                        .findFirst()
                        .flatMap(c -> c.delta().content())
                        .orElse("");
                if (!delta.isEmpty()) {
                    full.append(delta);
                    try {
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(delta)));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            onAssistantComplete.accept(full.toString());
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (UncheckedIOException e) {
            // Client disconnected — do not persist a partial assistant turn.
            emitter.completeWithError(e);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("LLM_UNAVAILABLE"));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    }
```

- [ ] **Step 5: Update `StubLlmGateway.streamChat` the same way**

Replace its `streamChat` method:
```java
    @Override
    public void streamChat(CaseSession session, String userMessage, SseEmitter emitter,
                           java.util.function.Consumer<String> onAssistantComplete) {
        try {
            String[] tokens = {"Dziękujemy", " za", " pytanie", "."};
            StringBuilder full = new StringBuilder();
            for (String token : tokens) {
                full.append(token);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(token)));
            }
            onAssistantComplete.accept(full.toString());
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
```
Add the import `import java.util.function.Consumer;` if you prefer the short type name (optional; the fully-qualified name above also compiles).

- [ ] **Step 6: Update `ChatService` — reload session, then persist the assistant reply**

Replace `app/backend/src/main/java/pl/nbp/copilot/application/ChatService.java` body of `streamChat`:
```java
    public void streamChat(String sessionId, String userMessage, SseEmitter emitter) {
        sessionStore.get(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        sessionStore.appendMessage(sessionId, new ChatMessage("user", userMessage, Instant.now()));

        // Reload so the context passed to the gateway includes the user message we just saved.
        // (With JpaSessionStore, get() returns a fresh object — the earlier reference would be stale.)
        var session = sessionStore.get(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        try {
            llmGateway.streamChat(session, userMessage, emitter, assistantText -> {
                if (assistantText != null && !assistantText.isBlank()) {
                    sessionStore.appendMessage(sessionId, new ChatMessage("assistant", assistantText, Instant.now()));
                }
            });
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("LLM_UNAVAILABLE"));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                // ignore - connection already closed
            }
        }
    }
```

- [ ] **Step 7: Run the `ChatService` test to verify it passes**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=ChatServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 8: Run the full backend suite (the gateway signature changed)**

Run: `cd app/backend && sh ./mvnw -q test`
Expected: PASS. `ChatControllerIntegrationTest` still streams stub tokens; it now also persists the assistant message (no assertion change required, but it must compile and pass).

- [ ] **Step 9: Commit**

```bash
git add app/backend/src/main/java/pl/nbp/copilot/llm/ \
        app/backend/src/main/java/pl/nbp/copilot/application/ChatService.java \
        app/backend/src/test/java/pl/nbp/copilot/application/ChatServiceTest.java
git commit -m "Backend: persist assistant chat replies and build chat context from the saved session"
```

---

### Task 4: Extend `GET /api/v1/cases/{id}` to include the decision

**Files:**
- Modify: `app/backend/src/main/java/pl/nbp/copilot/dto/CaseDetailResponse.java` (add `decision`)
- Modify: `app/backend/src/main/java/pl/nbp/copilot/web/CaseController.java` (populate `decision`)
- Test: `app/backend/src/test/java/pl/nbp/copilot/web/CaseDetailEndpointTest.java`

**Interfaces:**
- Consumes: `CaseSummaryDto`, `DecisionDto` (`{ category, justification, nextSteps, missingInfo }`), domain `ChatMessage`, `SessionStore`.
- Produces: `CaseDetailResponse(CaseSummaryDto caseSummary, DecisionDto decision, List<ChatMessage> transcript)`.

- [ ] **Step 1: Write the failing endpoint test**

`app/backend/src/test/java/pl/nbp/copilot/web/CaseDetailEndpointTest.java`:
```java
package pl.nbp.copilot.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import pl.nbp.copilot.model.*;
import pl.nbp.copilot.session.SessionStore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "stub-llm"})
class CaseDetailEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired SessionStore sessionStore;

    @Test
    void returnsCaseSummaryDecisionAndTranscript() throws Exception {
        var s = new CaseSession("get-1", CaseType.ZWROT, EquipmentCategory.AUDIO,
                "Sony WH-1000", LocalDate.of(2026, 6, 2), null);
        s.setDecision(new DecisionResult(DecisionCategory.ELIGIBLE, "uzasadnienie", "kroki", List.of()));
        s.getMessages().add(new ChatMessage("assistant", "Decyzja...", Instant.now()));
        s.getMessages().add(new ChatMessage("user", "Dzięki", Instant.now()));
        sessionStore.create(s);

        mockMvc.perform(get("/api/v1/cases/get-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseSummary.model").value("Sony WH-1000"))
                .andExpect(jsonPath("$.decision.category").value("ELIGIBLE"))
                .andExpect(jsonPath("$.transcript.length()").value(2))
                .andExpect(jsonPath("$.transcript[0].role").value("assistant"));
    }

    @Test
    void unknownSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/cases/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=CaseDetailEndpointTest`
Expected: FAIL — `$.decision` is missing (the response has no `decision` field yet).

- [ ] **Step 3: Add `decision` to `CaseDetailResponse`**

`app/backend/src/main/java/pl/nbp/copilot/dto/CaseDetailResponse.java`:
```java
package pl.nbp.copilot.dto;

import pl.nbp.copilot.model.ChatMessage;

import java.util.List;

public record CaseDetailResponse(CaseSummaryDto caseSummary, DecisionDto decision, List<ChatMessage> transcript) {}
```

- [ ] **Step 4: Populate `decision` in `CaseController.getCase`**

In `app/backend/src/main/java/pl/nbp/copilot/web/CaseController.java`, replace the body of `getCase`:
```java
    @GetMapping("/{sessionId}")
    public ResponseEntity<CaseDetailResponse> getCase(@PathVariable String sessionId) {
        var session = sessionStore.get(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        var caseSummary = new CaseSummaryDto(
            session.getType(),
            session.getCategory(),
            session.getModel(),
            session.getPurchaseDate()
        );

        var decisionResult = session.getDecision();
        var decision = decisionResult == null ? null : new DecisionDto(
            decisionResult.category(),
            decisionResult.justification(),
            decisionResult.nextSteps(),
            decisionResult.missingInfo()
        );

        return ResponseEntity.ok(new CaseDetailResponse(caseSummary, decision, session.getMessages()));
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd app/backend && sh ./mvnw -q test -Dtest=CaseDetailEndpointTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/backend/src/main/java/pl/nbp/copilot/dto/CaseDetailResponse.java \
        app/backend/src/main/java/pl/nbp/copilot/web/CaseController.java \
        app/backend/src/test/java/pl/nbp/copilot/web/CaseDetailEndpointTest.java
git commit -m "Backend: GET /cases/{id} returns decision + transcript for restore (ADR-004)"
```

---

### Task 5: Frontend — `getCase` API call + `CaseDetailResponse` model

**Files:**
- Modify: `app/frontend/src/app/core/models/index.ts` (add `CaseDetailResponse`)
- Modify: `app/frontend/src/app/core/api.service.ts` (add `getCase`)
- Test: `app/frontend/src/app/core/api.service.spec.ts` (extend)

**Interfaces:**
- Consumes: `DecisionDto`, `CaseSummaryDto`, `ChatMessage` (existing model types).
- Produces: `interface CaseDetailResponse { caseSummary: CaseSummaryDto; decision: DecisionDto | null; transcript: ChatMessage[]; }` and `ApiService.getCase(sessionId: string): Observable<CaseDetailResponse>`.

- [ ] **Step 1: Write the failing API test**

Add to `app/frontend/src/app/core/api.service.spec.ts` (inside the existing `describe('ApiService', ...)` block; the file already sets up `HttpTestingController` — follow its existing pattern for `httpMock`/`service`):
```typescript
  it('getCase issues GET and returns the case detail', () => {
    const detail = {
      caseSummary: { type: 'ZWROT', category: 'AUDIO', model: 'Sony', purchaseDate: '2026-06-02' },
      decision: { category: 'ELIGIBLE', justification: 'j', nextSteps: 'n', missingInfo: [] },
      transcript: [{ role: 'assistant', content: 'Decyzja' }],
    };

    let received: any;
    service.getCase('abc').subscribe((r) => (received = r));

    const req = httpMock.expectOne('/api/v1/cases/abc');
    expect(req.request.method).toBe('GET');
    req.flush(detail);

    expect(received.decision.category).toBe('ELIGIBLE');
    expect(received.transcript.length).toBe(1);
  });
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd app/frontend && npm test -- --watch=false --include='**/api.service.spec.ts'`
Expected: FAIL — `service.getCase` is not a function.

- [ ] **Step 3: Add the `CaseDetailResponse` model**

Append to `app/frontend/src/app/core/models/index.ts`:
```typescript
export interface CaseDetailResponse {
  caseSummary: CaseSummaryDto;
  decision: DecisionDto | null;
  transcript: ChatMessage[];
}
```

- [ ] **Step 4: Implement `getCase` in `ApiService`**

In `app/frontend/src/app/core/api.service.ts`, add the import and method:
```typescript
import { ApiError, CaseDetailResponse, SubmitCaseResponse } from './models';
```
```typescript
  getCase(sessionId: string): Observable<CaseDetailResponse> {
    return this.http.get<CaseDetailResponse>(`/api/v1/cases/${sessionId}`).pipe(
      catchError((err: HttpErrorResponse) => throwError(() => this.normalizeError(err)))
    );
  }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd app/frontend && npm test -- --watch=false --include='**/api.service.spec.ts'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/frontend/src/app/core/models/index.ts app/frontend/src/app/core/api.service.ts \
        app/frontend/src/app/core/api.service.spec.ts
git commit -m "Frontend: ApiService.getCase + CaseDetailResponse model"
```

---

### Task 6: Frontend — restore the chat from the URL on reload

**Files:**
- Modify: `app/frontend/src/app/features/chat/chat.component.ts` (restore in `ngOnInit`)
- Modify: `app/frontend/src/app/features/chat/chat.component.html` (loading + not-found states)
- Test: `app/frontend/src/app/features/chat/chat.component.spec.ts` (extend)

**Interfaces:**
- Consumes: `ApiService.getCase` (Task 5), `CaseDetailResponse`, `SubmitCaseResponse`, `ChatMessage`.
- Produces: restored `caseResponse` + `messages` signals; new `restoring` and `notFound` signals for template states.

- [ ] **Step 1: Write the failing component tests**

Extend `app/frontend/src/app/features/chat/chat.component.spec.ts`. Use the existing test harness in that file (it already provides a mocked `ApiService` and `ActivatedRoute`); add a spy for `getCase` returning `of(...)`. Add:
```typescript
  it('restores from getCase when there is no navigation state', async () => {
    // history.state has no `response`; route param sessionId = 'abc'
    apiSpy.getCase.and.returnValue(of({
      caseSummary: { type: 'ZWROT', category: 'AUDIO', model: 'Sony', purchaseDate: '2026-06-02' },
      decision: { category: 'ELIGIBLE', justification: 'j', nextSteps: 'n', missingInfo: [] },
      transcript: [
        { role: 'assistant', content: 'Decyzja...' },
        { role: 'user', content: 'Dzięki' },
      ],
    }));

    fixture.detectChanges(); // triggers ngOnInit
    await fixture.whenStable();

    expect(apiSpy.getCase).toHaveBeenCalledWith('abc');
    expect(component['messages']().length).toBe(2);
    expect(component['caseResponse']()!.decision.category).toBe('ELIGIBLE');
  });

  it('shows not-found when getCase 404s', async () => {
    apiSpy.getCase.and.returnValue(throwError(() => ({ code: 'SESSION_NOT_FOUND', message: 'x' })));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component['notFound']()).toBe(true);
  });
```
> Match the spec file's existing imports (`of`, `throwError` from `rxjs`) and its `ActivatedRoute` stub so `route.snapshot.params['sessionId']` returns `'abc'`. If the existing tests pass `history.state.response`, ensure these new tests run without it (the default).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd app/frontend && npm test -- --watch=false --include='**/chat.component.spec.ts'`
Expected: FAIL — `getCase` is never called (current `ngOnInit` only reads `history.state`) and `notFound` signal does not exist.

- [ ] **Step 3: Implement restore in `ChatComponent`**

In `app/frontend/src/app/features/chat/chat.component.ts`:

Add signals near the other `protected` signals:
```typescript
  protected restoring = signal(false);
  protected notFound = signal(false);
```
Replace `ngOnInit`:
```typescript
  ngOnInit(): void {
    const nav = history.state?.['response'] as SubmitCaseResponse | undefined;
    if (nav) {
      this.caseResponse.set(nav);
      this.messages.set([{ role: 'assistant', content: nav.firstMessage }]);
      return;
    }

    const sessionId = this.route.snapshot.params['sessionId'] as string;
    if (!sessionId) {
      this.notFound.set(true);
      return;
    }

    this.restoring.set(true);
    this.api.getCase(sessionId).subscribe({
      next: (detail) => {
        this.caseResponse.set({
          sessionId,
          decision: detail.decision ?? {
            category: 'NEEDS_HUMAN_REVIEW',
            justification: '',
            nextSteps: '',
          },
          firstMessage: detail.transcript[0]?.content ?? '',
          caseSummary: detail.caseSummary,
        });
        this.messages.set(
          detail.transcript.map((m) => ({ role: m.role, content: m.content })),
        );
        this.restoring.set(false);
      },
      error: () => {
        this.restoring.set(false);
        this.notFound.set(true);
      },
    });
  }
```

- [ ] **Step 4: Add loading + not-found UI (Polish)**

In `app/frontend/src/app/features/chat/chat.component.html`, add at the very top of `.chat-layout` (before the case-summary header):
```html
  @if (restoring()) {
    <div class="restoring" role="status">
      <mat-spinner diameter="28"></mat-spinner>
      <span>Wczytywanie rozmowy…</span>
    </div>
  }
  @if (notFound()) {
    <div class="chat-error-banner" role="alert">
      <mat-icon>error_outline</mat-icon>
      Nie znaleziono tej sprawy. <a routerLink="/">Wróć do formularza</a>.
    </div>
  }
```
Add `RouterLink` to the component imports for the link to work: in `chat.component.ts` import `import { ActivatedRoute, RouterLink } from '@angular/router';` and add `RouterLink` to the `imports` array of the `@Component` decorator.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd app/frontend && npm test -- --watch=false --include='**/chat.component.spec.ts'`
Expected: PASS (including the pre-existing chat tests).

- [ ] **Step 6: Lint + full frontend unit run**

Run: `cd app/frontend && npm run lint && npm test -- --watch=false`
Expected: no lint errors; all unit tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/frontend/src/app/features/chat/
git commit -m "Frontend: restore chat (header + transcript) from URL on reload; 404 handling"
```

---

### Task 7: E2E — reload restores the conversation (real stack)

**Files:**
- Modify: the existing Playwright journey spec under `app/frontend/e2e/` (or `app/e2e/` — locate with `find app -path '*e2e*' -name '*.spec.ts'`). Extend the happy-path test; do not create a parallel suite.

**Interfaces:**
- Consumes: the running real stack (backend `:8080` + frontend `:4200`), real LLM (per AGENTS.md, **nothing mocked in E2E**).

- [ ] **Step 1: Locate the existing E2E journey and its run command**

Run: `find app -path '*e2e*' -name '*.spec.ts'; sed -n '1,40p' app/frontend/package.json | grep -i e2e`
Expected: identifies the journey spec file and the `npm run e2e` (or equivalent) script. Read the existing happy-path test to reuse its selectors and setup.

- [ ] **Step 2: Add a reload-restore assertion to the happy-path journey**

After the test has submitted the form, landed on chat, and sent at least one follow-up that received a reply, append (adapt selectors to the existing spec):
```typescript
  // --- Restore-on-reload (ADR-004) ---
  // Capture the visible transcript before reload.
  const bubblesBefore = await page.locator('.bubble').allInnerTexts();
  expect(bubblesBefore.length).toBeGreaterThanOrEqual(3); // decision + user + assistant

  await page.reload();

  // The case-summary header (form data + decision badge) is restored from GET /cases/{id}.
  await expect(page.locator('.case-summary')).toBeVisible();
  await expect(page.locator('.decision-badge')).toBeVisible();

  // The full transcript is restored, not just the first decision message.
  const bubblesAfter = await page.locator('.bubble').allInnerTexts();
  expect(bubblesAfter.length).toBe(bubblesBefore.length);
  expect(bubblesAfter[bubblesAfter.length - 1]).toBe(bubblesBefore[bubblesBefore.length - 1]);
```

- [ ] **Step 3: Run the E2E suite against the real stack**

Per `MEMORY.md` runtime notes — start the backend with Java 21 and a real `OPENROUTER_API_KEY`, start the frontend, then run the E2E command identified in Step 1. Example:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
cd app/backend && set -a && . ./.env && set +a && sh ./mvnw spring-boot:run   # terminal A
cd app/frontend && npm start                                                   # terminal B
cd app/frontend && npm run e2e                                                 # terminal C (real LLM, nothing stubbed)
```
Expected: the happy-path journey passes, including the reload-restore assertions.

- [ ] **Step 4: Delete the leftover dev DB file (it is git-ignored, but keep the tree clean)**

Run: `rm -f app/backend/data/copilot.mv.db` (optional; the file is in `.gitignore`).

- [ ] **Step 5: Commit**

```bash
git add app/frontend/e2e   # adjust to the real E2E path from Step 1
git commit -m "E2E: assert chat restores fully on reload (ADR-004)"
```

---

## Self-Review

**Spec coverage (ADR-004):**
- §4.1 H2 file-backed → Task 1 (deps/config). §4.2 JPA behind seam + JSON columns → Tasks 1–2. §4.3 `ddl-auto=update` → Task 1. §4.4 retention indefinite → no purge built (constraint honored). §4.5 image not persisted → no image column anywhere (constraint honored).
- §5 GET contract (`caseSummary, decision, transcript`) → Task 4. §5 assistant-reply persistence → Task 3.
- §10 TAC-004-01 durability → Task 2 round-trip + restart-equivalent (fresh store reads same file in real run). TAC-004-02 value-object round-trip → Tasks 1–2. TAC-004-03 user+assistant persisted in order → Task 3. TAC-004-04 GET + 404 → Task 4. TAC-004-05 no image persisted → enforced by schema (no field). TAC-004-06 no SQLite/`load_extension` → never added.
- Frontend restore (ADR-004 §2 gap 3) → Tasks 5–6. E2E real-stack reload → Task 7.

**Placeholder scan:** none — every code step contains full code; every run step has an exact command and expected result.

**Type consistency:** `streamChat(..., Consumer<String> onAssistantComplete)` is defined in Task 3 and used identically across `LlmGateway`, `OpenRouterLlmGateway`, `StubLlmGateway`, and `ChatService`. `CaseDetailResponse(caseSummary, decision, transcript)` matches between Task 4 (backend record) and Task 5 (frontend interface). `getCase(sessionId)` signature matches across Tasks 5–6. `JpaSessionStore`/`InMemorySessionStore` profile gating (`!test` / `test`) is consistent with the existing `@ActiveProfiles({"test","stub-llm"})` suite.

**Known minor behaviors (intentional, noted in spec):** domain `CaseSession.createdAt` resets on entity→domain mapping (not surfaced in any response); `decision` may be `null` in the GET response only for a session that somehow has no decision (frontend falls back to a neutral badge).
