package ir.ipaam.kycservices.infrastructure.service.security;

import ir.ipaam.kycservices.infrastructure.service.security.dto.OcrTokenResponse;
import ir.ipaam.kycservices.infrastructure.service.security.props.OcrOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTokenProvider {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EXPIRY_SAFETY_WINDOW = Duration.ofSeconds(30);

    private final OcrOAuthProperties properties;

    @Qualifier("ocrAuthWebClient")
    private final WebClient authWebClient;

    private volatile TokenState cachedToken;

    public String getAccessToken() {
        TokenState tokenState = cachedToken;
        if (tokenState == null || tokenState.isExpired()) {
            synchronized (this) {
                tokenState = cachedToken;
                if (tokenState == null || tokenState.isExpired()) {
                    cachedToken = tokenState = fetchToken();
                }
            }
        }
        return tokenState.accessToken;
    }

    private TokenState fetchToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (StringUtils.hasText(properties.getClientId())) {
            formData.add("client_id", properties.getClientId());
        }
        if (StringUtils.hasText(properties.getClientSecret())) {
            formData.add("client_secret", properties.getClientSecret());
        }
        if (StringUtils.hasText(properties.getUsername())) {
            formData.add("username", properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            formData.add("password", properties.getPassword());
        }
        if (StringUtils.hasText(properties.getScope())) {
            formData.add("scope", properties.getScope());
        }
        formData.add("grant_type", properties.getGrantType());

        OcrTokenResponse response = authWebClient.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(OcrTokenResponse.class)
                .block(DEFAULT_TIMEOUT);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Failed to obtain access token for OCR service");
        }

        long expiresIn = response.expiresIn() != null ? response.expiresIn() : 0L;
        Instant expiresAt = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_SAFETY_WINDOW);
        if (expiresIn == 0L) {
            expiresAt = Instant.now().plusSeconds(60).minus(EXPIRY_SAFETY_WINDOW);
        }

        log.debug("Obtained new OCR access token expiring at {}", expiresAt);

        return new TokenState(response.accessToken(), expiresAt);
    }

    private record TokenState(String accessToken, Instant expiresAt) {

        private boolean isExpired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}
