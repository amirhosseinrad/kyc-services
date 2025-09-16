package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class KycProcessEventHandler {

    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;

    @EventHandler
    public void on(KycProcessStartedEvent event) {
        Customer customer = customerRepository.findByNationalCode(event.getNationalCode())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setNationalCode(event.getNationalCode());
                    return customerRepository.save(c);
                });

        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId(event.getProcessInstanceId());
        instance.setStatus("STARTED");
        instance.setStartedAt(LocalDateTime.now());
        instance.setCustomer(customer);

        kycProcessInstanceRepository.save(instance);
    }

    @EventHandler
    public void on(KycStatusUpdatedEvent event) {
        kycProcessInstanceRepository.findByCamundaInstanceId(event.getProcessInstanceId())
                .ifPresent(instance -> {
                    instance.setStatus(event.getStatus());

                    StepStatus stepStatus = new StepStatus();
                    stepStatus.setProcess(instance);
                    stepStatus.setStepName(event.getStepName());
                    stepStatus.setTimestamp(event.getUpdatedAt());
                    if (event.getState() != null) {
                        stepStatus.setState(StepStatus.State.valueOf(event.getState().toUpperCase(Locale.ROOT)));
                    }

                    instance.getStatuses().add(stepStatus);

                    kycProcessInstanceRepository.save(instance);
                    kycStepStatusRepository.save(stepStatus);
                });
    }

    @QueryHandler
    public ProcessInstance handle(FindKycStatusQuery query) {
        return kycProcessInstanceRepository
                .findTopByCustomer_NationalCodeOrderByStartedAtDesc(query.nationalCode())
                .orElseGet(() -> {
                    ProcessInstance instance = new ProcessInstance();
                    instance.setStatus("UNKNOWN");
                    return instance;
                });
    }
}
