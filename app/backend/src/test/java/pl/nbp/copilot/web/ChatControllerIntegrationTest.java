package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "stub-llm"})
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String submitCaseAndGetSessionId() throws Exception {
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        var image = new MockMultipartFile("image", "test.jpg", "image/jpeg", baos.toByteArray());

        var result = mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isOk())
            .andReturn();

        var responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("sessionId").asText();
    }

    @Test
    void chatReturnsTextEventStreamWithTokensAndDone() throws Exception {
        String sessionId = submitCaseAndGetSessionId();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/cases/" + sessionId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Czy mogę zwrócić produkt?\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvcResult.getAsyncResult(5000);

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));

        String responseBody = mvcResult.getResponse().getContentAsString();
        assertThat(responseBody).contains("data:");
        assertThat(responseBody).contains("event:done");
    }

    @Test
    void chatToNonexistentSessionReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/cases/nonexistent-id/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"Hello\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void chatWithEmptyMessageReturns400() throws Exception {
        String sessionId = submitCaseAndGetSessionId();

        mockMvc.perform(post("/api/v1/cases/" + sessionId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
