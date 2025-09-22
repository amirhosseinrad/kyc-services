package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadBiometricCommandTest {

    @Test
    void uploadSelfieCommandExposesProvidedValues() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1, 2, 3}, "selfie.png");

        UploadSelfieCommand command = new UploadSelfieCommand("proc-1", descriptor);

        assertEquals("proc-1", command.processInstanceId());
        assertSame(descriptor, command.selfieDescriptor());
    }

    @Test
    void uploadVideoCommandExposesProvidedValues() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{4, 5, 6}, "video.mp4");

        UploadVideoCommand command = new UploadVideoCommand("proc-2", descriptor, "token");

        assertEquals("proc-2", command.processInstanceId());
        assertSame(descriptor, command.videoDescriptor());
        assertEquals("token", command.inquiryToken());
    }
}
