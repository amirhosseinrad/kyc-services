package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing {@link KycProcessInstance} entities.
 */
@Repository
public interface KycProcessInstanceRepository extends JpaRepository<KycProcessInstance, Long> {

    /**
     * Finds the most recent process instance for a customer identified by national code.
     *
     * @param nationalCode national identification code of the customer
     * @return optional most recent KYC process instance
     */
    Optional<KycProcessInstance> findTopByCustomer_NationalCodeOrderByStartedAtDesc(String nationalCode);
}

