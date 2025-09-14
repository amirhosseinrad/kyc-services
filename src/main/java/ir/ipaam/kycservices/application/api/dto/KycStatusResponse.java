package ir.ipaam.kycservices.application.api.dto;

public record KycStatusResponse(
        String status,
        String error
) {
    public static KycStatusResponse success(String status) {
        return new KycStatusResponse(status, null);
    }

    public static KycStatusResponse error(String error) {
        return new KycStatusResponse(null, error);
    }
}
