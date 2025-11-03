package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.AddressVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressVerificationRepository extends JpaRepository<AddressVerification, Long> {

    Optional<AddressVerification> findTopByProcess_CamundaInstanceIdOrderByIdDesc(String camundaInstanceId);
}

