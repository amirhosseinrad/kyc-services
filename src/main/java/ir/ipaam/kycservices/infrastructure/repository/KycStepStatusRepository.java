package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link StepStatus} entities.
 */
@Repository
public interface KycStepStatusRepository extends JpaRepository<StepStatus, Long> {

    /**
     * Checks whether the given process has already recorded the supplied step name.
     *
     * @param camundaInstanceId the process identifier
     * @param stepName          the workflow step to check
     * @return {@code true} when a matching step record exists
     */
    boolean existsByProcess_CamundaInstanceIdAndStepName(String camundaInstanceId, String stepName);
}
