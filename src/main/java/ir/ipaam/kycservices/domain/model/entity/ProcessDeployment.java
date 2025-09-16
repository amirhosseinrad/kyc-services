package ir.ipaam.kycservices.domain.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "process_deployment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
