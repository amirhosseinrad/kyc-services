package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.domain.model.entity.Customer;
import java.time.LocalDate;

public record CustomerInfo(
        String nationalCode,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String mobile,
        String email
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
                customer.getEmail()
        );
    }
}
