package ir.ipaam.kycservices.infrastructure.service;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

import java.io.InputStream;
import java.util.Set;

public interface BpmnValidationService {

    BpmnModelInstance validateAndCheck(InputStream bpmnStream);

    Set<String> extractJobTypes(BpmnModelInstance modelInstance);
}
