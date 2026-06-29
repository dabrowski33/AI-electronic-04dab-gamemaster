package pl.nbp.copilot.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import pl.nbp.copilot.application.LlmUnavailableException;
import pl.nbp.copilot.application.PayloadTooLargeException;
import pl.nbp.copilot.application.SessionNotFoundException;
import pl.nbp.copilot.application.UnsupportedImageTypeException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test-errors")
    static class TestController {

        @GetMapping("/session-not-found")
        public String sessionNotFound() {
            throw new SessionNotFoundException("test-id");
        }

        @GetMapping("/llm-unavailable")
        public String llmUnavailable() {
            throw new LlmUnavailableException("LLM is down");
        }

        @GetMapping("/unsupported-image")
        public String unsupportedImage() {
            throw new UnsupportedImageTypeException("image/gif");
        }

        @GetMapping("/payload-too-large")
        public String payloadTooLarge() {
            throw new PayloadTooLargeException("File too large");
        }

        @GetMapping("/internal-error")
        public String internalError() {
            throw new RuntimeException("Something went wrong");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void sessionNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test-errors/session-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void llmUnavailableReturns502() throws Exception {
        mockMvc.perform(get("/test-errors/llm-unavailable"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
    }

    @Test
    void unsupportedImageReturns415() throws Exception {
        mockMvc.perform(get("/test-errors/unsupported-image"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void payloadTooLargeReturns413() throws Exception {
        mockMvc.perform(get("/test-errors/payload-too-large"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void unknownExceptionReturns500() throws Exception {
        mockMvc.perform(get("/test-errors/internal-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
