package ir.ipaam.kycservices.infrastructure.service.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OcrTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope
) {
}
