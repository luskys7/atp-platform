package com.atp.platform.service;

import com.atp.platform.entity.ScheduledJob;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduledJobRepository jobRepository;
    private final SuiteService suiteService;

    public List<ScheduledJob> list() {
        return jobRepository.findAll();
    }

    public ScheduledJob get(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "定时任务不存在", HttpStatus.NOT_FOUND));
    }

    public ScheduledJob create(Map<String, Object> body, Long userId) {
        validateCron(body.get("cron_expression").toString());
        ScheduledJob job = map(new ScheduledJob(), body);
        job.setCreatedBy(userId);
        return jobRepository.save(job);
    }

    public ScheduledJob update(Long id, Map<String, Object> body) {
        ScheduledJob job = get(id);
        if (body.containsKey("cron_expression")) validateCron(body.get("cron_expression").toString());
        map(job, body);
        return jobRepository.save(job);
    }

    public void delete(Long id) {
        jobRepository.deleteById(id);
    }

    public ScheduledJob toggle(Long id, boolean enabled) {
        ScheduledJob job = get(id);
        job.setEnabled(enabled);
        return jobRepository.save(job);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledJob job : jobRepository.findByEnabledTrue()) {
            try {
                CronExpression cron = CronExpression.parse(job.getCronExpression());
                LocalDateTime last = job.getLastTriggeredAt() != null ? job.getLastTriggeredAt() : now.minusMinutes(2);
                LocalDateTime next = cron.next(last);
                if (next != null && !next.isAfter(now)) {
                    log.info("Trigger scheduled job {} suite {}", job.getId(), job.getSuiteId());
                    suiteService.runSuite(job.getSuiteId(), job.getCreatedBy() != null ? job.getCreatedBy() : 1L);
                    job.setLastTriggeredAt(now);
                    jobRepository.save(job);
                }
            } catch (Exception e) {
                log.warn("Schedule tick failed for job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    private void validateCron(String expr) {
        try {
            CronExpression.parse(expr);
        } catch (Exception e) {
            throw new AppException("INVALID", "Cron 表达式无效: " + expr, HttpStatus.BAD_REQUEST);
        }
    }

    private ScheduledJob map(ScheduledJob job, Map<String, Object> body) {
        if (body.containsKey("name")) job.setName(body.get("name").toString());
        if (body.containsKey("suite_id")) job.setSuiteId(Long.valueOf(body.get("suite_id").toString()));
        if (body.containsKey("cron_expression")) job.setCronExpression(body.get("cron_expression").toString());
        if (body.containsKey("enabled")) job.setEnabled(Boolean.valueOf(body.get("enabled").toString()));
        if (body.containsKey("priority") && body.get("priority") != null) {
            job.setPriority(Byte.valueOf(body.get("priority").toString()));
        }
        return job;
    }
}
