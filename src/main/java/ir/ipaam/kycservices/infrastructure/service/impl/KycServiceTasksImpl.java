package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.infrastructure.model.KycProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycServiceTasksImpl implements KycServiceTasks {

    private static final Logger log = LoggerFactory.getLogger(KycServiceTasksImpl.class);

    private final CustomerRepository customerRepository;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @Override
    public void validateNationalCodeChecksum(String nationalCode, String processInstanceId) {
        if (nationalCode == null || !nationalCode.matches("\\d{10}")) {
            throw new IllegalArgumentException("National code must be exactly 10 digits");
        }

        // Reject codes where all digits are the same
        boolean allSame = nationalCode.chars().allMatch(ch -> ch == nationalCode.charAt(0));
        if (allSame) {
            throw new IllegalArgumentException("National code cannot contain all identical digits");
        }

        int checksumDigit = Character.getNumericValue(nationalCode.charAt(9));
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = Character.getNumericValue(nationalCode.charAt(i));
            sum += digit * (10 - i);
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? remainder : 11 - remainder;

        if (checksumDigit != expected) {
            String message = String.format("Invalid national code checksum for process %s", processInstanceId);
            log.warn(message);
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public void callNationalRegistry(String nationalCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void sendOtp(String mobile, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void checkOtp(String mobile, String otpCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void checkMobileFormat(String mobile, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void callShahkarService(String nationalCode, String mobile, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public String checkKycStatus(String nationalCode) {
        if (customerRepository == null || kycProcessInstanceRepository == null) {
            log.warn("Repositories not initialized, returning UNKNOWN status");
            return "UNKNOWN";
        }
        return kycProcessInstanceRepository
                .findTopByCustomer_NationalCodeOrderByStartedAtDesc(nationalCode)
                .map(KycProcessInstance::getStatus)
                .orElse("UNKNOWN");
    }

    @Override
    public void updateKycStatus(String processInstanceId, String status) {
        // TODO: implement integration
    }

    @Override
    public void logFailureAndRetry(String stepName, String reason, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void runOcrExtraction(Long documentId, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void compareOcrWithIdentity(Long documentId, String nationalCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void runFraudCheck(Long documentId, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void validateZipCode(String zipCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void storeAddress(String address, String zipCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void storeCardTrackingNumber(String trackingNumber, String processInstanceId) {
        // TODO: implement integration
    }
}

