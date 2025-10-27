package ir.ipaam.kycservices.config;

import ir.ipaam.kycservices.infrastructure.service.security.EsbTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class EsbClientConfig {

    @Bean
    @Qualifier("cardOcrWebClient")
    public WebClient cardOcrWebClient(@Value("${ocr.card.base-url}") String baseUrl,
                                      WebClient.Builder builder,
                                      EsbTokenProvider tokenProvider) {
        return buildAuthorizedClient(baseUrl, builder, tokenProvider);
    }
    @Bean
    @Qualifier("bookletValidationWebClient")
    public WebClient bookletValidationWebClient(
            @Value("${ocr.booklet.base-url}") String baseUrl,
            WebClient.Builder builder,
            EsbTokenProvider tokenProvider) {
        return buildAuthorizedClient(baseUrl, builder, tokenProvider);
    }

    @Bean
    @Qualifier("esbAuthWebClient")
    public WebClient esbAuthWebClient(WebClient.Builder builder) {
        return builder.build();
    }

    private WebClient buildAuthorizedClient(String baseUrl,
                                            WebClient.Builder builder,
                                            EsbTokenProvider tokenProvider) {
        ExchangeFilterFunction authorizationFilter = (request, next) -> Mono.defer(() -> {
            String accessToken = tokenProvider.getAccessToken();
            ClientRequest authenticatedRequest = ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .build();
            return next.exchange(authenticatedRequest);
        });

        return builder
                .baseUrl(baseUrl)
                .filter(authorizationFilter)
                .build();
    }
}
