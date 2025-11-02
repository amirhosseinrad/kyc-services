package ir.ipaam.kycservices;

import org.axonframework.messaging.timeout.UnitOfWorkTimeoutInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KycServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(KycServicesApplication.class, args);
    }

    @Bean
    public UnitOfWorkTimeoutInterceptor timeoutInterceptor() {
        // 300 seconds = 5 minutes
        return new UnitOfWorkTimeoutInterceptor("validateBooklet", 900_000, 900_000, 900_000);
    }

}
