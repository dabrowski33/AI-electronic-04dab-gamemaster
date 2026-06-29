---
name: feedback-jpeg-fixture
description: Always generate test JPEG fixtures using Java AWT or a real image tool — synthetic hex blobs fail ImageIO
metadata:
  type: feedback
---

A hand-crafted minimal JPEG hex blob causes `ImageIO.read()` to return `null` in the backend `ImageCompressor`, triggering a NullPointerException → 500 INTERNAL_ERROR.

**Why:** Java's ImageIO is strict. A JPEG must have correct SOF/DHT/SOS/DQT markers in the right order with valid values; many "minimal" examples from the internet are not actually decodable by ImageIO.

**How to apply:** Always generate test JPEG fixtures using Java AWT:
```java
BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
ImageIO.write(img, "JPEG", new File("test-upload.jpg"));
```
Or in this project, `app/fixtures/test-upload.jpg` already exists and is 631 bytes — reuse it.
