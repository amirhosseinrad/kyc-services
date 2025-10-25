package ir.ipaam.kycservices;

import ir.ipaam.kycservices.config.OcrClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(OcrClientConfig.class)
public class KycServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(KycServicesApplication.class, args);
    }

}
