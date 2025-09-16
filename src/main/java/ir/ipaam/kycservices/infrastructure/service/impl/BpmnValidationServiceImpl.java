package ir.ipaam.kycservices.infrastructure.service.impl;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import ir.ipaam.kycservices.infrastructure.service.BpmnValidationService;
import ir.ipaam.kycservices.infrastructure.zeebe.JobWorkerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BpmnValidationServiceImpl implements BpmnValidationService {

    private final JobWorkerRegistry jobWorkerRegistry;

    @Override
    public BpmnModelInstance validateAndCheck(InputStream bpmnStream) {
        // Parse & validate BPMN
        BpmnModelInstance modelInstance = Bpmn.readModelFromStream(bpmnStream);
        Bpmn.validateModel(modelInstance);

        // Extract job types and verify workers
        Set<String> jobTypes = extractJobTypes(modelInstance);
        List<String> missing = jobTypes.stream()
                .filter(type -> !jobWorkerRegistry.isRegistered(type))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing JobWorkers for types: " + String.join(", ", missing));
        }

        return modelInstance;
    }

    @Override
    public Set<String> extractJobTypes(BpmnModelInstance modelInstance) {
        Set<String> jobTypes = new HashSet<>();
        Collection<ServiceTask> serviceTasks = modelInstance.getModelElementsByType(ServiceTask.class);
        for (ServiceTask task : serviceTasks) {
            ZeebeTaskDefinition def = task.getSingleExtensionElement(ZeebeTaskDefinition.class);
            if (def != null && def.getType() != null) {
                jobTypes.add(def.getType());
            }
        }
        return jobTypes;
    }
}
