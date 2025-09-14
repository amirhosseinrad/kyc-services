package ir.ipaam.kycservices.eventHandler.projection;

import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.domain.model.entity.KycStatusView;
import ir.ipaam.kycservices.infrastructure.repository.KycStatusViewRepository;
import lombok.RequiredArgsConstructor;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KycStatusProjection {

    private final KycStatusViewRepository repository;

    @EventHandler
    public void on(KycProcessStartedEvent event) {
        repository.save(new KycStatusView(event.getNationalCode(), "STARTED"));
    }

    @EventHandler
    public void on(KycStatusUpdatedEvent event) {
        repository.save(new KycStatusView(event.getNationalCode(), event.getStatus()));
    }

    @QueryHandler
    public String handle(FindKycStatusQuery query) {
        return repository.findById(query.getNationalCode())
                .map(KycStatusView::getStatus)
                .orElse("UNKNOWN");
    }
}
