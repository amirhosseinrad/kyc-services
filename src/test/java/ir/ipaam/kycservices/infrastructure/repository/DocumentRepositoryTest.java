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

        Document older = new Document();
        older.setType("PHOTO");
        older.setStoragePath("bucket/older");
        older.setProcess(process);
        entityManager.persist(older);

        Document newer = new Document();
        newer.setType("PHOTO");
        newer.setStoragePath("bucket/newer");
        newer.setProcess(process);
        entityManager.persist(newer);

        entityManager.flush();
        entityManager.clear();

        Optional<Document> result = documentRepository
                .findTopByTypeAndProcess_Customer_NationalCodeOrderByIdDesc("PHOTO", "0012345678");

        assertThat(result).isPresent();
        assertThat(result.get().getStoragePath()).isEqualTo("bucket/newer");
    }
}
