package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycUserTasksImpl implements KycUserTasks {

    static final String FRONT_FILENAME = "front-image";
    static final String BACK_FILENAME = "back-image";

    private final CommandGateway commandGateway;

    @Override
    public void uploadCardDocuments(byte[] frontImage, byte[] backImage, String processInstanceId) {
        validateInput(frontImage, backImage, processInstanceId);

        DocumentPayloadDescriptor frontDescriptor = new DocumentPayloadDescriptor(frontImage, randomizeFilename(FRONT_FILENAME));
        DocumentPayloadDescriptor backDescriptor = new DocumentPayloadDescriptor(backImage, randomizeFilename(BACK_FILENAME));

        commandGateway.sendAndWait(new UploadCardDocumentsCommand(
                processInstanceId,
                frontDescriptor,
                backDescriptor
        ));
    }

    private void validateInput(byte[] frontImage, byte[] backImage, String processInstanceId) {
        if (frontImage == null || frontImage.length == 0) {
            throw new IllegalArgumentException("frontImage must be provided");
        }
        if (backImage == null || backImage.length == 0) {
            throw new IllegalArgumentException("backImage must be provided");
        }
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
    }

    private String randomizeFilename(String baseName) {
        return baseName + "-" + UUID.randomUUID();
    }

    @Override
    public void uploadSignature(byte[] signatureImage, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void acceptConsent(String termsVersion, boolean accepted, String processInstanceId) {
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("termsVersion must be provided");
        }
        if (!accepted) {
            throw new IllegalArgumentException("accepted must be true");
        }
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }

        String normalizedProcessId = processInstanceId.trim();
        String normalizedTermsVersion = termsVersion.trim();

        commandGateway.sendAndWait(new AcceptConsentCommand(
                normalizedProcessId,
                normalizedTermsVersion,
                true
        ));
    }

    @Override
    public void provideAddress(String address, String zipCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadSelfieAndVideo(byte[] photo, byte[] video, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadIdPages(java.util.List<byte[]> pages, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadCardBranchSelfieAndVideo(byte[] photo, byte[] video, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void provideEnglishPersonalInfo(String firstNameEn, String lastNameEn, String email, String telephone, String processInstanceId) {
        // TODO: implement integration
    }
}
