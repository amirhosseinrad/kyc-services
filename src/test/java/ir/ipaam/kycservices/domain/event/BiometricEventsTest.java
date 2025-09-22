package ir.ipaam.kycservices.domain.event;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BiometricEventsTest {

    @Test
    void selfieUploadedEventRetainsMetadata() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{7, 8, 9}, "selfie.jpg");
        LocalDateTime uploadedAt = LocalDateTime.now();

        SelfieUploadedEvent event = new SelfieUploadedEvent("proc-1", "123", descriptor, uploadedAt);

        assertEquals("proc-1", event.getProcessInstanceId());
        assertEquals("123", event.getNationalCode());
        assertSame(descriptor, event.getDescriptor());
        assertEquals(uploadedAt, event.getUploadedAt());
        assertTrue(Arrays.equals(descriptor.data(), event.getDescriptor().data()));
    }

    @Test
    void videoUploadedEventRetainsMetadata() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{10, 11, 12}, "video.mp4");
        LocalDateTime uploadedAt = LocalDateTime.now();

        VideoUploadedEvent event = new VideoUploadedEvent("proc-2", "456", "token", descriptor, uploadedAt);

        assertEquals("proc-2", event.getProcessInstanceId());
        assertEquals("456", event.getNationalCode());
        assertEquals("token", event.getInquiryToken());
        assertSame(descriptor, event.getDescriptor());
        assertEquals(uploadedAt, event.getUploadedAt());
        assertTrue(Arrays.equals(descriptor.data(), event.getDescriptor().data()));
    }
}
