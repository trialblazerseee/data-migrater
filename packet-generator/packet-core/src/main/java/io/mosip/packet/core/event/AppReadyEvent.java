package io.mosip.packet.core.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

public class AppReadyEvent extends ApplicationEvent {

    private static final long serialVersionUID = 2691767965391398786L;

    public AppReadyEvent(Object source) {
        super(source);
    }
}
