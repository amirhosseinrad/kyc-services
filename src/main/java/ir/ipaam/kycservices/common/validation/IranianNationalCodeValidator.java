package ir.ipaam.kycservices.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IranianNationalCodeValidator implements ConstraintValidator<IranianNationalCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String trimmed = value.trim();
        if (trimmed.length() != 10 || !trimmed.chars().allMatch(Character::isDigit)) {
            return false;
        }

        if (trimmed.chars().distinct().count() == 1) {
            return false;
        }

        int checkDigit = trimmed.charAt(9) - '0';
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = trimmed.charAt(i) - '0';
            sum += digit * (10 - i);
        }

        int remainder = sum % 11;
        if (remainder < 2) {
            return checkDigit == remainder;
        }
        return checkDigit == (11 - remainder);
    }
}
