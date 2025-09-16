package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessDeploymentRepository extends JpaRepository<ProcessDeployment, String> {
    Optional<ProcessDeployment> findByFileHash(String fileHash);
}
