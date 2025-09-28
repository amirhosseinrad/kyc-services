package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.Consent;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, Long> {
    Optional<Consent> findByProcessAndAccepted(ProcessInstance process, boolean accepted);}
