package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.KycStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link KycStepStatus} entities.
 */
@Repository
public interface KycStepStatusRepository extends JpaRepository<KycStepStatus, Long> {
}
