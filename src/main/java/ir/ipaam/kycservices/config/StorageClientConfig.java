package ir.ipaam.kycservices.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class StorageClientConfig {

    @Bean
    @Qualifier("cardDocumentWebClient")
    public WebClient cardDocumentWebClient(@Value("${storage.card-service.base-url}") String baseUrl,
                                           WebClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
