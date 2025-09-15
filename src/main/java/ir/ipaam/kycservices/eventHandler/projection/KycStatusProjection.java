package ir.ipaam.kycservices.eventHandler.projection;

import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.domain.model.entity.KycStatusView;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStatusViewRepository;
import lombok.RequiredArgsConstructor;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KycStatusProjection {

    private final KycStatusViewRepository repository;
    private final KycProcessInstanceRepository processRepository;
    private final CustomerRepository customerRepository;

    @EventHandler
    public void on(KycProcessStartedEvent event) {
        Customer customer = customerRepository.findByNationalCode(event.getNationalCode()).orElse(null);

        KycStatusView view = new KycStatusView();
        view.setNationalCode(event.getNationalCode());
        view.setStatus("STARTED");
        view.setCamundaInstanceId(event.getProcessInstanceId());
        view.setStartedAt(event.getStartedAt());
        view.setCustomer(customer);
        repository.save(view);
    }

    @EventHandler
    public void on(KycStatusUpdatedEvent event) {
        KycStatusView view = repository.findById(event.getNationalCode()).orElseGet(() -> {
            KycStatusView v = new KycStatusView();
            v.setNationalCode(event.getNationalCode());
            return v;
        });

        Customer customer = customerRepository.findByNationalCode(event.getNationalCode()).orElse(null);

        view.setCamundaInstanceId(event.getProcessInstanceId());
        view.setStatus(event.getStatus());
        view.setCustomer(customer);

        if (view.getStartedAt() == null) {
            view.setStartedAt(event.getUpdatedAt());
        }

        if ("COMPLETED".equalsIgnoreCase(event.getStatus())) {
            view.setCompletedAt(event.getUpdatedAt());
        }

        repository.save(view);
    }

    @QueryHandler
    public String handle(FindKycStatusQuery query) {
        return repository.findById(query.getNationalCode())
                .map(KycStatusView::getStatus)
                .orElse("UNKNOWN");
    }
}
