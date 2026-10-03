package edu.cit.nunez;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InstanceLifecycle {

    private volatile boolean registered = false;
    private volatile long lastHeartbeatMs = 0L;

    public boolean isRegistered() {
        return registered;
    }

    public synchronized void recordHeartbeat() {
        this.registered = true;
        this.lastHeartbeatMs = System.currentTimeMillis();
    }

    public boolean hasRecentHeartbeat() {
        return registered && (System.currentTimeMillis() - lastHeartbeatMs) < 25000L;
    }

    @EventListener
    public void onRegistered(InstanceRegisteredEvent event) {
        registered = true;
        lastHeartbeatMs = System.currentTimeMillis();
    }
}
