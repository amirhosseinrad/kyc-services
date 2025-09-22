package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.infrastructure.service.dto.GenerateTempTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

@Service
@Slf4j
public class InquiryTokenServiceImpl implements InquiryTokenService {

    private final WebClient inquiryWebClient;

    public InquiryTokenServiceImpl(@Qualifier("inquiryWebClient") WebClient inquiryWebClient) {
        this.inquiryWebClient = inquiryWebClient;
    }

    @Override
    public Optional<String> generateToken(String processInstanceId) {
        GenerateTempTokenResponse response;
        try {
            response = inquiryWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/Inquiry/GenerateTempToken")
                            .queryParam("tempTokenValue", processInstanceId)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(GenerateTempTokenResponse.class)
                    .block();
        } catch (Exception ex) {
            log.error("Failed to generate inquiry token for process {}", processInstanceId, ex);
            return Optional.empty();
        }

        if (response == null) {
            log.warn("Inquiry service returned empty response for process {} when generating token", processInstanceId);
            return Optional.empty();
        }

        Integer responseCode = response.getResponseCode();
        if (responseCode != null && responseCode != 0) {
            String message = response.getResponseMessage();
            if (response.getException() != null && response.getException().getErrorMessage() != null) {
                message = response.getException().getErrorMessage();
            }
            log.error("Inquiry service reported error code {} for process {} when generating token: {}",
                    responseCode, processInstanceId, message);
            return Optional.empty();
        }

        String token = response.getResult();
        if (token == null || token.isBlank()) {
            log.warn("Inquiry service returned empty token for process {}", processInstanceId);
            return Optional.empty();
        }

        return Optional.of(token);
    }
}

