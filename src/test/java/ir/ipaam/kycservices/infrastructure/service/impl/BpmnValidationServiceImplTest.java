package ir.ipaam.kycservices.infrastructure.service.impl;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import ir.ipaam.kycservices.infrastructure.zeebe.JobWorkerRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmnValidationServiceImplTest {

    @Test
    void shouldValidateKycProcessAndExposeAllJobTypes() throws Exception {
        Set<String> expectedTypes = Set.of(
                "check-kyc-status",
                "upload-card-documents",
                "accept-consent",
                "upload-signature",
                "upload-selfie",
                "upload-video",
                "upload-id-pages",
                "provide-english-personal-info"
        );

        JobWorkerRegistry registry = mock(JobWorkerRegistry.class);
        when(registry.isRegistered(anyString()))
                .thenAnswer(invocation -> expectedTypes.contains(invocation.getArgument(0, String.class)));

        BpmnValidationServiceImpl validationService = new BpmnValidationServiceImpl(registry);

        try (InputStream modelStream = getClass().getResourceAsStream("/bpmn/kyc-process.bpmn")) {
            assertThat(modelStream).as("kyc-process.bpmn should be available on the classpath").isNotNull();

            BpmnModelInstance modelInstance = assertDoesNotThrow(() -> validationService.validateAndCheck(modelStream));

            assertThat(validationService.extractJobTypes(modelInstance))
                    .containsExactlyInAnyOrderElementsOf(expectedTypes);
        }
    }
}
