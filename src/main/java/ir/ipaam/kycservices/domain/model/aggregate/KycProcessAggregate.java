package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.time.LocalDateTime;

@Aggregate
@NoArgsConstructor
public class KycProcessAggregate {

    @AggregateIdentifier
    private String processInstanceId;
    private String nationalCode;
    private String status;

    @CommandHandler
    public KycProcessAggregate(StartKycProcessCommand command) {
        AggregateLifecycle.apply(new KycProcessStartedEvent(
                command.getProcessInstanceId(),
                command.getNationalCode(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UpdateKycStatusCommand command) {
        AggregateLifecycle.apply(new KycStatusUpdatedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getStatus(),
                command.getStepName(),
                command.getState(),
                LocalDateTime.now()));
    }

    @EventSourcingHandler
    public void on(KycProcessStartedEvent event) {
        this.processInstanceId = event.getProcessInstanceId();
        this.nationalCode = event.getNationalCode();
        this.status = "STARTED";
    }

    @EventSourcingHandler
    public void on(KycStatusUpdatedEvent event) {
        this.status = event.getStatus();
    }
}
