package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing {@link ProcessInstance} entities.
 */
@Repository
public interface KycProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    /**
     * Finds the most recent process instance for a customer identified by national code.
     *
     * @param nationalCode national identification code of the customer
     * @return optional most recent KYC process instance
     */
    Optional<ProcessInstance> findTopByCustomer_NationalCodeOrderByStartedAtDesc(String nationalCode);

    /**
     * Finds a KYC process instance by its Camunda process instance identifier.
     *
     * @param camundaInstanceId Camunda process instance id
     * @return optional process instance for the given Camunda id
     */
    Optional<ProcessInstance> findByCamundaInstanceId(String camundaInstanceId);
}

