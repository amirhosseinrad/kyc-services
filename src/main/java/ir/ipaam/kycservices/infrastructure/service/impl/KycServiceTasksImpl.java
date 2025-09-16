package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
                    ResponseTypes.instanceOf(ProcessInstance.class)).join();
        } catch (Exception e) {
            log.error("Failed to query KYC status", e);
            return null;
        }
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

