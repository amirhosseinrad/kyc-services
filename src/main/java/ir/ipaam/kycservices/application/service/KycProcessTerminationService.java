package ir.ipaam.kycservices.application.service;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Provides operations to stop previously started Camunda KYC workflow instances.
 */
public interface KycProcessTerminationService {

    /**
     * Cancels the workflow identified by the supplied {@code processInstanceId} and
     * updates the persisted KYC state accordingly.
     *
     * @param processInstanceId Camunda process instance identifier
     * @return response payload describing the cancellation result
     */
    ResponseEntity<Map<String, Object>> cancelProcess(String processInstanceId);
}

