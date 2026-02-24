package com.microservices.Monolith_example.DatabaseClearer;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

//singleton by default!
@Component
public class DowntimeLock {

    private final AtomicBoolean downtime = new AtomicBoolean(false);

    public boolean isDowntime() {
        return downtime.get();
    }

    public void startDowntime() {
        downtime.set(true);
    }

    public void endDowntime() {
        downtime.set(false);
    }
}
