package pl.nbp.copilot.application;

public class UnsupportedImageTypeException extends RuntimeException {
    public UnsupportedImageTypeException(String contentType) {
        super("Unsupported image type: " + contentType);
    }
}
