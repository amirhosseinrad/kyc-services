package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
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
    static final String SELFIE_FILENAME = "selfie-image";
    static final String VIDEO_FILENAME = "video-file";
    static final String SIGNATURE_FILENAME = "signature-image";

    private final CommandGateway commandGateway;

    @Override
    public void uploadCardDocuments(byte[] frontImage, byte[] backImage, String processInstanceId) {
        validateDocument(frontImage, "frontImage");
        validateDocument(backImage, "backImage");
        String normalizedProcessInstanceId = normalizeProcessInstanceId(processInstanceId);

        DocumentPayloadDescriptor frontDescriptor = new DocumentPayloadDescriptor(frontImage, randomizeFilename(FRONT_FILENAME));
        DocumentPayloadDescriptor backDescriptor = new DocumentPayloadDescriptor(backImage, randomizeFilename(BACK_FILENAME));

        commandGateway.sendAndWait(new UploadCardDocumentsCommand(
                normalizedProcessInstanceId,
                frontDescriptor,
                backDescriptor
        ));
    }

    private void validateDocument(byte[] document, String fieldName) {
        if (document == null || document.length == 0) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }

    private String randomizeFilename(String baseName) {
        return baseName + "-" + UUID.randomUUID();
    }

    @Override
    public void uploadSignature(byte[] signatureImage, String processInstanceId) {
        validateDocument(signatureImage, "signatureImage");
        String normalizedProcessInstanceId = normalizeProcessInstanceId(processInstanceId);

        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(signatureImage, randomizeFilename(SIGNATURE_FILENAME));

        commandGateway.sendAndWait(new UploadSignatureCommand(
                normalizedProcessInstanceId,
                descriptor
        ));
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
    public void uploadSelfie(byte[] selfie, String processInstanceId) {
        validateDocument(selfie, "selfie");
        String normalizedProcessInstanceId = normalizeProcessInstanceId(processInstanceId);

        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(selfie, randomizeFilename(SELFIE_FILENAME));

        commandGateway.sendAndWait(new UploadSelfieCommand(
                normalizedProcessInstanceId,
                descriptor
        ));
    }

    @Override
    public void uploadVideo(byte[] video, String processInstanceId) {
        validateDocument(video, "video");
        String normalizedProcessInstanceId = normalizeProcessInstanceId(processInstanceId);

        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(video, randomizeFilename(VIDEO_FILENAME));

        commandGateway.sendAndWait(new UploadVideoCommand(
                normalizedProcessInstanceId,
                descriptor
        ));
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
