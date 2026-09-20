package com.wanyuecity.energy.system.migration;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MigrationMaintenance {
    private final AtomicBoolean active = new AtomicBoolean(false);

    public boolean isActive() { return active.get(); }

    public boolean begin() { return active.compareAndSet(false, true); }

    public void end() { active.set(false); }
}
