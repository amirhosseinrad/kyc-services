package ir.ipaam.kycservices.application.command;

import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardDocumentCommandHandler {

    private final KycUserTasks kycUserTasks;

    @CommandHandler
    public void handle(UploadCardDocumentsCommand command) {
        log.debug("Handling card document upload for process {}", command.getProcessInstanceId());
        kycUserTasks.uploadCardDocuments(
                command.getFrontDescriptor().data(),
                command.getBackDescriptor().data(),
                command.getProcessInstanceId()
        );
    }
}
