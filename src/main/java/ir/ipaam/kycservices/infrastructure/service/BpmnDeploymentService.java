package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.InputStream;

public interface BpmnDeploymentService {
    @Transactional
    ProcessDeployment deployIfChanged( InputStream bpmnStream) throws IOException;
}
