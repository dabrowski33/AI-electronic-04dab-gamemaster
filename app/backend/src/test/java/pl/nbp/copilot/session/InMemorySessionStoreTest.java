package pl.nbp.copilot.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.ChatMessage;
import pl.nbp.copilot.model.EquipmentCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionStoreTest {

    private InMemorySessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore();
    }

    private CaseSession createSession(String id) {
        return new CaseSession(id, CaseType.ZWROT, EquipmentCategory.LAPTOPY_I_KOMPUTERY,
                "Test Model", LocalDate.now(), null);
    }

    @Test
    void createAndGetLifecycle() {
        var session = createSession("sess-1");
        store.create(session);

        var retrieved = store.get("sess-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getSessionId()).isEqualTo("sess-1");
    }

    @Test
    void existsReturnsTrueAfterCreate() {
        var session = createSession("sess-2");
        assertThat(store.exists("sess-2")).isFalse();

        store.create(session);
        assertThat(store.exists("sess-2")).isTrue();
    }

    @Test
    void appendMessageAddsMessage() {
        var session = createSession("sess-3");
        store.create(session);

        store.appendMessage("sess-3", new ChatMessage("user", "Hello", Instant.now()));
        store.appendMessage("sess-3", new ChatMessage("assistant", "Hi", Instant.now()));

        var retrieved = store.get("sess-3").orElseThrow();
        assertThat(retrieved.getMessages()).hasSize(2);
    }

    @Test
    void concurrentAppendsAreSafe() throws InterruptedException {
        var session = createSession("sess-4");
        store.create(session);

        int threads = 10;
        int appendsPerThread = 10;
        var latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < appendsPerThread; i++) {
                        store.appendMessage("sess-4", new ChatMessage("user", "msg", Instant.now()));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        var retrieved = store.get("sess-4").orElseThrow();
        assertThat(retrieved.getMessages()).hasSize(threads * appendsPerThread);
    }
}
