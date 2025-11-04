package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.domain.model.entity.Customer;
import java.time.LocalDate;

public record CustomerInfo(
        String nationalCode,
        String firstNameFa,
        String lastNameFa,
        String firstNameEn,
        String lastNameEn,
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
                customer.getFirstName_fa(),
                customer.getLastName_fa(),
                customer.getFirstName_en(),
                customer.getLastName_en(),
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
