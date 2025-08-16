package io.mosip.packet.core.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AppReadyEventPublisher {
    private ApplicationEventPublisher publisher;

    public AppReadyEventPublisher (ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    public void publishEvent() {
        publisher.publishEvent(new AppReadyEvent(this));
    }
}
