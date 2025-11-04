package ir.ipaam.kycservices.domain.query;

import ir.ipaam.kycservices.common.validation.IranianNationalCode;

public record FindKycStatusQuery(@IranianNationalCode String nationalCode) {}
