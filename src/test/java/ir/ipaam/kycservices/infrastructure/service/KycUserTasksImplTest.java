package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.infrastructure.service.impl.KycUserTasksImpl;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    void uploadSelfiePropagatesGatewayErrors() {
        byte[] selfie = "selfie".getBytes(StandardCharsets.UTF_8);

        doThrow(new RuntimeException("boom")).when(commandGateway).sendAndWait(any());

        assertThrows(RuntimeException.class, () -> tasks.uploadSelfie(selfie, "process-1"));
    }

    @Test
    void uploadSignatureDispatchesCommand() {
        byte[] signature = "signature".getBytes(StandardCharsets.UTF_8);

        tasks.uploadSignature(signature, " process-1 ");

        ArgumentCaptor<UploadSignatureCommand> captor = ArgumentCaptor.forClass(UploadSignatureCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        UploadSignatureCommand command = captor.getValue();
        assertEquals("process-1", command.processInstanceId());
        assertNotNull(command.signatureDescriptor());
        assertTrue(command.signatureDescriptor().filename().startsWith(KycUserTasksImpl.SIGNATURE_FILENAME));
        assertArrayEquals(signature, command.signatureDescriptor().data());
    }

    @Test
    void uploadSignatureValidatesInput() {
        byte[] signature = "signature".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSignature(null, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSignature(new byte[0], "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadSignature(signature, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void uploadSignaturePropagatesGatewayErrors() {
        byte[] signature = "signature".getBytes(StandardCharsets.UTF_8);

        doThrow(new RuntimeException("boom")).when(commandGateway).sendAndWait(any());

        assertThrows(RuntimeException.class, () -> tasks.uploadSignature(signature, "process-1"));
    }

    @Test
    void uploadVideoDispatchesCommand() {
        byte[] video = "video".getBytes(StandardCharsets.UTF_8);

        tasks.uploadVideo(video, " process-1 ");

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
    void uploadVideoPropagatesGatewayErrors() {
        byte[] video = "video".getBytes(StandardCharsets.UTF_8);

        doThrow(new RuntimeException("boom")).when(commandGateway).sendAndWait(any());

        assertThrows(RuntimeException.class, () -> tasks.uploadVideo(video, "process-1"));
    }

    @Test
    void uploadIdPagesDispatchesCommand() {
        List<byte[]> pages = List.of(
                "page1".getBytes(StandardCharsets.UTF_8),
                "page2".getBytes(StandardCharsets.UTF_8)
        );

        tasks.uploadIdPages(pages, " process-1 ");

        ArgumentCaptor<UploadIdPagesCommand> captor = ArgumentCaptor.forClass(UploadIdPagesCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        UploadIdPagesCommand command = captor.getValue();
        assertEquals("process-1", command.processInstanceId());
        assertEquals(2, command.pageDescriptors().size());
        assertArrayEquals(pages.get(0), command.pageDescriptors().get(0).data());
        assertArrayEquals(pages.get(1), command.pageDescriptors().get(1).data());
        assertTrue(command.pageDescriptors().get(0).filename()
                .startsWith(KycUserTasksImpl.ID_PAGE_FILENAME + "-1-"));
        assertTrue(command.pageDescriptors().get(1).filename()
                .startsWith(KycUserTasksImpl.ID_PAGE_FILENAME + "-2-"));
    }

    @Test
    void uploadIdPagesValidatesInput() {
        List<byte[]> validPages = List.of("page".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadIdPages(null, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadIdPages(List.of(), "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadIdPages(List.of(new byte[0]), "process-1"));
        List<byte[]> pagesWithNull = new ArrayList<>();
        pagesWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadIdPages(pagesWithNull, "process-1"));
        assertThrows(IllegalArgumentException.class, () ->
                tasks.uploadIdPages(List.of(new byte[1], new byte[1], new byte[1], new byte[1], new byte[1]), "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadIdPages(validPages, " "));

        verify(commandGateway, never()).sendAndWait(any());
    }

    @Test
    void uploadIdPagesPropagatesGatewayErrors() {
        List<byte[]> pages = List.of("page".getBytes(StandardCharsets.UTF_8));

        doThrow(new RuntimeException("boom")).when(commandGateway).sendAndWait(any());

        assertThrows(RuntimeException.class, () -> tasks.uploadIdPages(pages, "process-1"));
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

    @Test
    void provideEnglishPersonalInfoDispatchesCommand() {
        tasks.provideEnglishPersonalInfo(" John ", " Doe ", " john.doe@example.com ", " 0912 ", " process-1 ");

        ArgumentCaptor<ProvideEnglishPersonalInfoCommand> captor = ArgumentCaptor.forClass(ProvideEnglishPersonalInfoCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());

        ProvideEnglishPersonalInfoCommand command = captor.getValue();
        assertEquals("process-1", command.processInstanceId());
        assertEquals("John", command.firstNameEn());
        assertEquals("Doe", command.lastNameEn());
        assertEquals("john.doe@example.com", command.email());
        assertEquals("0912", command.telephone());
    }

    @Test
    void provideEnglishPersonalInfoValidatesInput() {
        assertThrows(IllegalArgumentException.class, () ->
                tasks.provideEnglishPersonalInfo("", "Doe", "john.doe@example.com", "0912", "process-1"));
        assertThrows(IllegalArgumentException.class, () ->
                tasks.provideEnglishPersonalInfo("John", " ", "john.doe@example.com", "0912", "process-1"));
        assertThrows(IllegalArgumentException.class, () ->
                tasks.provideEnglishPersonalInfo("John", "Doe", "invalid", "0912", "process-1"));
        assertThrows(IllegalArgumentException.class, () ->
                tasks.provideEnglishPersonalInfo("John", "Doe", "john.doe@example.com", " ", "process-1"));
        assertThrows(IllegalArgumentException.class, () ->
                tasks.provideEnglishPersonalInfo("John", "Doe", "john.doe@example.com", "0912", " "));

        verify(commandGateway, never()).sendAndWait(any(ProvideEnglishPersonalInfoCommand.class));
    }
}
