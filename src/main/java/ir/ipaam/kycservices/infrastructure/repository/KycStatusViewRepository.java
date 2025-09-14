package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.KycStatusView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycStatusViewRepository extends JpaRepository<KycStatusView, String> {
}
