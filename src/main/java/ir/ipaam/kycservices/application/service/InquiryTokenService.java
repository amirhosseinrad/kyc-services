package ir.ipaam.kycservices.application.service;

import java.util.Optional;

/**
 * Service abstraction for retrieving inquiry access tokens.
 */
public interface InquiryTokenService {

    /**
     * Attempts to generate a short-lived inquiry token for the provided process instance identifier.
     *
     * @param processInstanceId the Camunda process instance identifier associated with the inquiry
     * @return an {@link Optional} containing the generated token when the remote call succeeds, or an empty optional when
     * the inquiry service reports a business error
     * @throws ir.ipaam.kycservices.domain.exception.InquiryTokenException when the inquiry service cannot be reached or the
     * response cannot be decoded
     */
    Optional<String> generateToken(String processInstanceId);
}

