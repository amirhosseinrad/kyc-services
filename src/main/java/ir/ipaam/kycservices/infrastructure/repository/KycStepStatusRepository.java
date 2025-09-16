package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link StepStatus} entities.
 */
@Repository
public interface KycStepStatusRepository extends JpaRepository<StepStatus, Long> {
}
