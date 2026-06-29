package pl.nbp.copilot.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!stub-llm")
public class OpenAiClientConfig {

    @Bean
    public OpenAIClient openAIClient(LlmProperties props) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .build();
    }
}
