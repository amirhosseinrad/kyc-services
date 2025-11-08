package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.CancelProcessResponse;

/**
 * Provides operations to stop previously started Camunda KYC workflow instances.
 */
public interface ProcessService {

    /**
     * Cancels the workflow identified by the supplied {@code processInstanceId} and
     * updates the persisted KYC state accordingly.
     *
     * @param processInstanceId Camunda process instance identifier
     * @return response payload describing the cancellation result
     */
    CancelProcessResponse cancelProcess(String processInstanceId);
}
