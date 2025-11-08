package ir.ipaam.kycservices.application.service.dto;

import java.util.Map;

public record AddressCollectionResponse(
        String processInstanceId,
        String postalCode,
        String address,
        String status,
        String validationStatus,
        Boolean zipValid,
        Map<String, Object> validation,
        boolean alreadyProcessed
) {
}
