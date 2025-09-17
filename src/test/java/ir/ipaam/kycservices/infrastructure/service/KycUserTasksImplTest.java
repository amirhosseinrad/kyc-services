package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.infrastructure.service.impl.KycUserTasksImpl;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycUserTasksImplTest {

    private CommandGateway commandGateway;
    private KycUserTasksImpl tasks;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        tasks = new KycUserTasksImpl(commandGateway);
    }

    @Test
    void uploadCardDocumentsDispatchesCommand() {
        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        tasks.uploadCardDocuments(front, back, "process-1");

        ArgumentCaptor<UploadCardDocumentsCommand> captor = ArgumentCaptor.forClass(UploadCardDocumentsCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        UploadCardDocumentsCommand command = captor.getValue();
        assertEquals("process-1", command.getProcessInstanceId());
        assertNotNull(command.getFrontDescriptor());
        assertNotNull(command.getBackDescriptor());
        assertTrue(command.getFrontDescriptor().filename().startsWith(KycUserTasksImpl.FRONT_FILENAME));
        assertTrue(command.getBackDescriptor().filename().startsWith(KycUserTasksImpl.BACK_FILENAME));
        assertArrayEquals(front, command.getFrontDescriptor().data());
        assertArrayEquals(back, command.getBackDescriptor().data());
    }

    @Test
    void uploadCardDocumentsValidatesInput() {
        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(null, back, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(front, new byte[0], "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(front, back, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void uploadCardDocumentsPropagatesGatewayErrors() {
        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        doThrow(new RuntimeException("boom")).when(commandGateway).sendAndWait(any());

        assertThrows(RuntimeException.class, () -> tasks.uploadCardDocuments(front, back, "process-1"));
    }

    @Test
    void uploadSelfieDispatchesCommand() {
        byte[] selfie = "selfie".getBytes(StandardCharsets.UTF_8);

        tasks.uploadSelfie(selfie, " process-1 ");

        ArgumentCaptor<UploadSelfieCommand> captor = ArgumentCaptor.forClass(UploadSelfieCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        UploadSelfieCommand command = captor.getValue();
        assertEquals("process-1", command.processInstanceId());
        assertNotNull(command.selfieDescriptor());
        assertTrue(command.selfieDescriptor().filename().startsWith(KycUserTasksImpl.SELFIE_FILENAME));
        assertArrayEquals(selfie, command.selfieDescriptor().data());
    }

    @Test
    void uploadSelfieValidatesInput() {
        byte[] selfie = "selfie".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSelfie(null, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSelfie(new byte[0], "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSelfie(selfie, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void uploadVideoDispatchesCommand() {
        byte[] video = "video".getBytes(StandardCharsets.UTF_8);

        tasks.uploadVideo(video, "process-1");

        ArgumentCaptor<UploadVideoCommand> captor = ArgumentCaptor.forClass(UploadVideoCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        UploadVideoCommand command = captor.getValue();
        assertEquals("process-1", command.processInstanceId());
        assertNotNull(command.videoDescriptor());
        assertTrue(command.videoDescriptor().filename().startsWith(KycUserTasksImpl.VIDEO_FILENAME));
        assertArrayEquals(video, command.videoDescriptor().data());
    }

    @Test
    void uploadVideoValidatesInput() {
        byte[] video = "video".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadVideo(null, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadVideo(new byte[0], "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadVideo(video, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void acceptConsentDispatchesCommand() {
        tasks.acceptConsent("v1", true, "process-1");

        ArgumentCaptor<AcceptConsentCommand> captor = ArgumentCaptor.forClass(AcceptConsentCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        AcceptConsentCommand command = captor.getValue();
        assertEquals("process-1", command.getProcessInstanceId());
        assertEquals("v1", command.getTermsVersion());
        assertTrue(command.isAccepted());
    }

    @Test
    void acceptConsentValidatesInput() {
        assertThrows(IllegalArgumentException.class, () -> tasks.acceptConsent("", true, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.acceptConsent("v1", false, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.acceptConsent("v1", true, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }
}
