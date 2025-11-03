package ir.ipaam.kycservices.application.api.dto;

/**
 * Request payload for collecting and validating a customer's address and postal code.
 */
public record AddressVerificationRequest(
        String processInstanceId,
        String postalCode,
        String address
) {
}

