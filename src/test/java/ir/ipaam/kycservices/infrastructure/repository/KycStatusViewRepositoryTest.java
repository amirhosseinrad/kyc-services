package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.KycStatusView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KycStatusViewRepositoryTest {

    @Autowired
    private KycStatusViewRepository repository;

    @Test
    @DisplayName("findByCamundaInstanceId returns matching entity")
    void findByCamundaInstanceId_returnsEntity() {
        KycStatusView view = new KycStatusView("NC01", "CAM123", "STARTED");
        repository.save(view);

        Optional<KycStatusView> result = repository.findByCamundaInstanceId("CAM123");
        assertThat(result).isPresent();
        assertThat(result.get().getNationalCode()).isEqualTo("NC01");
    }

    @Test
    @DisplayName("findByCamundaInstanceId returns empty when not found")
    void findByCamundaInstanceId_returnsEmpty() {
        assertThat(repository.findByCamundaInstanceId("UNKNOWN")).isEmpty();
    }
}
