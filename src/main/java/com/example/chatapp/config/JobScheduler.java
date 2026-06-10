package com.example.chatapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
public class JobScheduler {
    private final JobLauncher jobLauncher;
    private final Job redisToDbJob;

    @Value("${app.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Scheduled(fixedRate = 60000)
    public void runRedisToDbJob() {
        if (!schedulerEnabled) {
            return;
        }

        log.info("Starting Redis to DB batch job...");
        try {
            // JobParameters must be unique for each run. Using a timestamp is a common practice.
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(redisToDbJob, jobParameters);
        } catch (Exception e) {
            log.error("Exception while running batch job", e);
        }
    }
}
