package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.INQUIRY_TOKEN_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InquiryTokenServiceImplTest {

    @Test
    void generateTokenWrapsWebClientExceptions() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://inquiry.test")
                .exchangeFunction(request -> Mono.error(
                        WebClientResponseException.create(
                                HttpStatus.BAD_GATEWAY.value(),
                                "Bad Gateway",
                                HttpHeaders.EMPTY,
                                new byte[0],
                                StandardCharsets.UTF_8)))
                .build();

        InquiryTokenServiceImpl service = new InquiryTokenServiceImpl(webClient);

        InquiryTokenException ex = assertThrows(InquiryTokenException.class,
                () -> service.generateToken("process-123"));
        assertEquals(INQUIRY_TOKEN_FAILED, ex.getMessage());
        assertTrue(ex.getCause() instanceof WebClientResponseException);
    }

    @Test
    void generateTokenPropagatesUnexpectedRuntimeExceptions() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://inquiry.test")
                .exchangeFunction(request -> Mono.error(new IllegalStateException("boom")))
                .build();

        InquiryTokenServiceImpl service = new InquiryTokenServiceImpl(webClient);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generateToken("process-123"));
        assertEquals("boom", ex.getMessage());
    }
}
