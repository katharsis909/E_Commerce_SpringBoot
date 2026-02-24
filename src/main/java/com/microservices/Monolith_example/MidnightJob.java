package com.microservices.Monolith_example;

import com.microservices.Monolith_example.DatabaseClearer.DowntimeLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MidnightJob {
    DowntimeLock downtimeLock;

    public MidnightJob(DowntimeLock downtimeLock) {
        this.downtimeLock = downtimeLock;
    }

    // Runs every day at 00:00
    //repeats at 0th second, 0th minute, 0th hour & all multiples of week,month & year (0 is the modulo)
    @Scheduled(cron = "0 0 0 * * *")
    public void runMidnight() {
        System.out.println("Scheduler triggered. Entering downtime...");
        downtimeLock.startDowntime();

        try {
            // Simulate some work
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        //very good, finally is a good thing!
        finally {
            downtimeLock.endDowntime();
            System.out.println("Work done. Exiting downtime...");
        }
    }
}
