package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.domain.model.entity.Customer;
import java.time.LocalDate;

public record CustomerInfo(
        String nationalCode,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String mobile,
        String email,
        Boolean hasNewNationalCard,
        String fatherName,
        LocalDate cardExpirationDate,
        String cardSerialNumber,
        String cardBarcode,
        String cardOcrFrontTrackId,
        String cardOcrBackTrackId
) {
    public static CustomerInfo from(Customer customer) {
        if (customer == null) {
            return null;
        }
        return new CustomerInfo(
                customer.getNationalCode(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getBirthDate(),
                customer.getMobile(),
                customer.getEmail(),
                customer.getHasNewNationalCard(),
                customer.getFatherName(),
                customer.getCardExpirationDate(),
                customer.getCardSerialNumber(),
                customer.getCardBarcode(),
                customer.getCardOcrFrontTrackId(),
                customer.getCardOcrBackTrackId()
        );
    }
}
