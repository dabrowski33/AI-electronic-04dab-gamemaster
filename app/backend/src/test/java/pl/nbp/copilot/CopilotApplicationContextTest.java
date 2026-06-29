package pl.nbp.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({"test", "stub-llm"})
class CopilotApplicationContextTest {
    @Test
    void contextLoads() {
        // Spring context loads without error
    }
}
