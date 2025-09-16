package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "KYC_PROCESS_DEPLOYMENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ProcessDeployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processId;      // extracted from BPMN
    private String fileHash;       // SHA-256 hash of BPMN file
    private long deploymentKey;    // returned by Zeebe
    private int processVersion;    // returned by Zeebe
    private LocalDateTime deployedAt;
}
