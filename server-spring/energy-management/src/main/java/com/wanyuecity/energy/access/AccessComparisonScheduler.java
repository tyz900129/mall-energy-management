package com.wanyuecity.energy.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessComparisonScheduler {
    private final AccessControlService service;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void refreshPersonComparison(){
        try{service.startPersonScan("SYSTEM_DAILY");}
        catch(Exception ex){log.warn("daily access person comparison skipped: {}",ex.getMessage());}
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void refreshCardComparison(){
        try{service.startCardScan("SYSTEM_DAILY");}
        catch(Exception ex){log.warn("daily access card comparison skipped: {}",ex.getMessage());}
    }
}
