package ir.ipaam.kycservices.infrastructure.service.security;

import ir.ipaam.kycservices.infrastructure.service.security.dto.OcrTokenResponse;
import ir.ipaam.kycservices.infrastructure.service.security.props.OcrOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class EsbTokenProvider {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration EXPIRY_SAFETY_WINDOW = Duration.ofSeconds(30);
    private final OcrOAuthProperties properties;
    private final WebClient authWebClient;

    private volatile TokenState cachedToken;

    public EsbTokenProvider(OcrOAuthProperties properties,
                            @Qualifier("esbAuthWebClient")
                            WebClient authWebClient) {
        this.properties = properties;
        this.authWebClient = authWebClient;
    }

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

        // First attempt: use injected authWebClient (preferred)
        try {
            OcrTokenResponse response = requestTokenWithClient(authWebClient, formData);
            return tokenFromResponse(response);
        } catch (Exception ex) {
            // If cause is SSL handshake, we will attempt insecure fallback
            if (isSslHandshakeFailure(ex)) {
                log.warn("SSL handshake failed with injected authWebClient — retrying with insecure WebClient (development only). Cause: {}", ex.toString());
                WebClient insecure = buildInsecureWebClient();
                try {
                    OcrTokenResponse response = requestTokenWithClient(insecure, formData);
                    log.warn("Successfully obtained token using insecure WebClient. REMOVE insecure fallback for production.");
                    return tokenFromResponse(response);
                } catch (Exception ex2) {
                    log.error("Retry with insecure WebClient also failed", ex2);
                    throw new IllegalStateException("Failed to obtain access token for OCR service (insecure retry failed)", ex2);
                }
            } else {
                log.error("Failed to obtain OCR token with injected client and error is not SSL handshake", ex);
                throw new IllegalStateException("Failed to obtain access token for OCR service", ex);
            }
        }
    }

    private boolean isSslHandshakeFailure(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SSLHandshakeException) return true;
            // Some reactor/netty wrappers will nest the SSLHandshakeException -> inspect message too
            String msg = cause.getMessage();
            if (msg != null && msg.toLowerCase().contains("sslhandshakeexception")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private OcrTokenResponse requestTokenWithClient(WebClient client, MultiValueMap<String, String> formData) {
        return client.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(OcrTokenResponse.class)
                .block(DEFAULT_TIMEOUT);
    }

    private TokenState tokenFromResponse(OcrTokenResponse response) {
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

    /**
     * Build a WebClient which accepts all certificates (InsecureTrustManagerFactory).
     * WARNING: This disables certificate validation — use only for development / debugging.
     */
    private WebClient buildInsecureWebClient() {
        HttpClient httpClient = HttpClient.create()
                .secure(spec -> {
                    try {
                        spec.sslContext(
                                SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                        );
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                });

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private record TokenState(String accessToken, Instant expiresAt) {
        private boolean isExpired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}
