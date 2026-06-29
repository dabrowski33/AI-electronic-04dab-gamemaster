package pl.nbp.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.nbp.copilot.config.ImageProperties;
import pl.nbp.copilot.config.LlmProperties;

@SpringBootApplication
@EnableConfigurationProperties({LlmProperties.class, ImageProperties.class})
public class CopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CopilotApplication.class, args);
    }
}
