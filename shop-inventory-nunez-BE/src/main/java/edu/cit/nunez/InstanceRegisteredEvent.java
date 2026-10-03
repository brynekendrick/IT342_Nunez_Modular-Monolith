package edu.cit.nunez;

import org.springframework.context.ApplicationEvent;

public class InstanceRegisteredEvent extends ApplicationEvent {

    public InstanceRegisteredEvent(Object source) {
        super(source);
    }
}
