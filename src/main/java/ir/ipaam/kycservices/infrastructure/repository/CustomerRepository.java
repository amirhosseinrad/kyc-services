package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for accessing {@link Customer} entities.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds a customer by its national code.
     *
     * @param nationalCode national identification code of the customer
     * @return optional customer
     */
    Optional<Customer> findByNationalCode(String nationalCode);
}

