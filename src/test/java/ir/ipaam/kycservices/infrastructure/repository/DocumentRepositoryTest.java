package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findTopByTypeAndProcessCustomerNationalCodeReturnsLatestDocument() {
        Customer customer = new Customer();
        customer.setNationalCode("0012345678");
        entityManager.persist(customer);

        ProcessInstance process = new ProcessInstance();
        process.setCustomer(customer);
        entityManager.persist(process);

        Document verifiedOld = new Document();
        verifiedOld.setType("PHOTO");
        verifiedOld.setStoragePath("bucket/verified-old");
        verifiedOld.setProcess(process);
        verifiedOld.setVerified(true);
        entityManager.persist(verifiedOld);

        Document unverified = new Document();
        unverified.setType("PHOTO");
        unverified.setStoragePath("bucket/unverified");
        unverified.setProcess(process);
        unverified.setVerified(false);
        entityManager.persist(unverified);

        Document verifiedNew = new Document();
        verifiedNew.setType("PHOTO");
        verifiedNew.setStoragePath("bucket/verified-new");
        verifiedNew.setProcess(process);
        verifiedNew.setVerified(true);
        entityManager.persist(verifiedNew);

        entityManager.flush();
        entityManager.clear();

        Optional<Document> result = documentRepository
                .findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678");

        assertThat(result).isPresent();
        assertThat(result.get().getStoragePath()).isEqualTo("bucket/verified-new");
    }
}
