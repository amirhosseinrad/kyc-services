package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.KYC_STATUS_QUERY_FAILED;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class KycServiceTasksImpl implements KycServiceTasks {

    private static final Logger log = LoggerFactory.getLogger(KycServiceTasksImpl.class);

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    @Override
    public ProcessInstance checkKycStatus(String nationalCode) {
        if (queryGateway == null) {
            log.warn("QueryGateway not initialized, returning null");
            return null;
        }
        try {
            return queryGateway.query(new FindKycStatusQuery(nationalCode),
                    ResponseTypes.instanceOf(ProcessInstance.class)).get();
        } catch (NoHandlerForQueryException | QueryExecutionException e) {
            log.error("Failed to query KYC status", e);
            throw queryFailed(e);
        } catch (CompletionException e) {
            log.error("Failed to query KYC status", e);
            throw queryFailed(e);
        } catch (ExecutionException e) {
            log.error("Failed to query KYC status", e);
            throw queryFailed(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to query KYC status", e);
            throw queryFailed(e);
        }
    }

    private IllegalStateException queryFailed(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return new IllegalStateException(KYC_STATUS_QUERY_FAILED, cause);
    }

    @Override
    public void logFailureAndRetry(String stepName, String reason, String processInstanceId) {
        if (commandGateway == null) {
            log.warn("CommandGateway not initialized, unable to log failure for process {}", processInstanceId);
            return;
        }

        UpdateKycStatusCommand command = new UpdateKycStatusCommand(
                processInstanceId,
                reason,
                stepName,
                "FAILED");
        commandGateway.sendAndWait(command);
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

