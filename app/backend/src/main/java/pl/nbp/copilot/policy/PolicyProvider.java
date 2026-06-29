package pl.nbp.copilot.policy;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.model.CaseType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PolicyProvider {

    private final String zwrotPolicy;
    private final String reklamacjaPolicy;

    public PolicyProvider() throws IOException {
        this.zwrotPolicy = loadPolicy("policies/polityka-zwrotow.md");
        this.reklamacjaPolicy = loadPolicy("policies/polityka-reklamacji.md");
    }

    private String loadPolicy(String path) throws IOException {
        var resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    public String getPolicy(CaseType type) {
        return switch (type) {
            case ZWROT -> zwrotPolicy;
            case REKLAMACJA -> reklamacjaPolicy;
        };
    }
}
