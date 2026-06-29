package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "stub-llm"})
class CaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMultipartFile createValidImage() throws Exception {
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);

        return new MockMultipartFile("image", "test.jpg", "image/jpeg", baos.toByteArray());
    }

    @Test
    void validZwrotSubmitReturns200WithEligibleDecision() throws Exception {
        var image = createValidImage();

        mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").isNotEmpty())
            .andExpect(jsonPath("$.decision.category").value("ELIGIBLE"))
            .andExpect(jsonPath("$.firstMessage").isNotEmpty());
    }

    @Test
    void blankModelReturns400WithValidationError() throws Exception {
        var image = createValidImage();

        mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "   ")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fields.model").exists());
    }

    @Test
    void missingReasonForReklamacjaReturns400() throws Exception {
        var image = createValidImage();

        mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "REKLAMACJA")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void futurePurchaseDateReturns400() throws Exception {
        var image = createValidImage();

        mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2099-01-15"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fields.purchaseDate").exists());
    }

    @Test
    void gifImageReturns415() throws Exception {
        var gifImage = new MockMultipartFile("image", "test.gif", "image/gif",
            new byte[]{0x47, 0x49, 0x46, 0x38}); // GIF magic bytes

        mockMvc.perform(multipart("/api/v1/cases")
                .file(gifImage)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void imageLargerThan10MbReturns413() throws Exception {
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        var largeImage = new MockMultipartFile("image", "large.jpg", "image/jpeg", largeBytes);

        mockMvc.perform(multipart("/api/v1/cases")
                .file(largeImage)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void getCaseReturns200WithSessionData() throws Exception {
        var image = createValidImage();

        // Submit a case first
        var result = mockMvc.perform(multipart("/api/v1/cases")
                .file(image)
                .param("type", "ZWROT")
                .param("category", "LAPTOPY_I_KOMPUTERY")
                .param("model", "MacBook Pro 14")
                .param("purchaseDate", "2024-01-15"))
            .andExpect(status().isOk())
            .andReturn();

        var responseBody = result.getResponse().getContentAsString();
        var sessionId = objectMapper.readTree(responseBody).get("sessionId").asText();

        // Get the case
        mockMvc.perform(get("/api/v1/cases/" + sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caseSummary.type").value("ZWROT"))
            .andExpect(jsonPath("$.caseSummary.model").value("MacBook Pro 14"))
            .andExpect(jsonPath("$.transcript").isArray());
    }

    @Test
    void getNonexistentCaseReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/cases/nonexistent-session-id"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
