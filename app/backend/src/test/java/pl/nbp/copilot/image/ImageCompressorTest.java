package pl.nbp.copilot.image;

import org.junit.jupiter.api.Test;
import pl.nbp.copilot.config.ImageProperties;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCompressorTest {

    private final ImageProperties props = new ImageProperties(1024, 0.85);
    private final ImageCompressor compressor = new ImageCompressor(props);

    @Test
    void largeImageIsDownscaledAndReencodedAsJpeg() throws IOException {
        // 2048x1536 image
        BufferedImage img = new BufferedImage(2048, 1536, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 2048, 1536);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        byte[] inputBytes = baos.toByteArray();

        byte[] output = compressor.compress(inputBytes, "image/jpeg");

        // Must be JPEG (starts with 0xFF 0xD8)
        assertThat(output[0] & 0xFF).isEqualTo(0xFF);
        assertThat(output[1] & 0xFF).isEqualTo(0xD8);

        // Long edge must be <= 1024
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        int longEdge = Math.max(result.getWidth(), result.getHeight());
        assertThat(longEdge).isLessThanOrEqualTo(1024);

        // Output should be smaller than input (or at least not vastly larger)
        assertThat(output.length).isLessThan(inputBytes.length);
    }

    @Test
    void smallImageIsNotUpscaled() throws IOException {
        // 100x80 image
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 80);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        byte[] inputBytes = baos.toByteArray();

        byte[] output = compressor.compress(inputBytes, "image/jpeg");

        // Long edge must stay <= 100 (not upscaled)
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        int longEdge = Math.max(result.getWidth(), result.getHeight());
        assertThat(longEdge).isLessThanOrEqualTo(100);
    }
}
