package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Customer {
    @Id
    @GeneratedValue
    private Long id;
    private String nationalCode;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String mobile;
    private String email;
}
