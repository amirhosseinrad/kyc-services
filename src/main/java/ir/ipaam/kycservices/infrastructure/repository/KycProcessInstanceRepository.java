package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.infrastructure.model.KycProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for accessing {@link KycProcessInstance} entities.
 */
public interface KycProcessInstanceRepository extends JpaRepository<KycProcessInstance, Long> {

    /**
     * Finds the most recent process instance for a customer identified by national code.
     *
     * @param nationalCode national identification code of the customer
     * @return optional most recent KYC process instance
     */
    Optional<KycProcessInstance> findTopByCustomer_NationalCodeOrderByStartedAtDesc(String nationalCode);
}

