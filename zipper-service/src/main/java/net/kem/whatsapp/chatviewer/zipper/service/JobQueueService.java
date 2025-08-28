package net.kem.whatsapp.chatviewer.zipper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.zipper.model.ProcessingResult;
import net.kem.whatsapp.chatviewer.zipper.model.ZipProcessingJob;
import net.kem.whatsapp.chatviewer.zipper.repository.ZipProcessingJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobQueueService {

    private final ZipProcessingJobRepository jobRepository;
    private final ZipProcessingService zipProcessingService;
    private final NotificationService notificationService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3); // Configurable

    /**
     * Process pending jobs every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void processPendingJobs() {
        try {
            List<ZipProcessingJob> pendingJobs = jobRepository.findActiveJobsOrderedByPriority();
            if (pendingJobs.isEmpty()) {
                return;
            }

            log.info("Found {} pending jobs to process", pendingJobs.size());
            for (ZipProcessingJob job : pendingJobs) {
                // Check if we should process this job (not already being processed)
                if (job.getStatus() == ZipProcessingJob.JobStatus.PENDING
                    || job.getStatus() == ZipProcessingJob.JobStatus.RETRY) {
                    processJobAsync(job);
                }
            }

        } catch (Exception e) {
            log.error("Error processing pending jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a single job asynchronously
     */
    private void processJobAsync(ZipProcessingJob job) {
        CompletableFuture.runAsync(() -> {
            try {
                job.setStatus(ZipProcessingJob.JobStatus.PROCESSING);
                log.info("Starting async processing of job: {} (user: {})", job.getJobId(),
                        job.getUserId());

                ProcessingResult result = zipProcessingService.processZipFile(job);

                // Notify main service about completion
                notificationService.notifyMainService(result);

                log.info("Completed processing job: {} with result: {}", job.getJobId(), result.isSuccess());

                job.setStatus(ZipProcessingJob.JobStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Error processing job: {} - {}", job.getJobId(), e.getMessage(), e);
                job.setErrorMessage(e.getMessage());

                if(job.getAttempts() < job.getMaxAttempts()) {
                    job.setStatus(ZipProcessingJob.JobStatus.RETRY);
                    job.setAttempts(job.getAttempts() + 1);
                } else {
                    job.setStatus(ZipProcessingJob.JobStatus.FAILED);
                }
            } finally {
                jobRepository.save(job);
            }
        }, executorService);
    }

    /**
     * Retry failed jobs that haven't exceeded max attempts
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void retryFailedJobs() {
        try {
            List<ZipProcessingJob> retryableJobs = jobRepository.findRetryableFailedJobs();

            if (retryableJobs.isEmpty()) {
                return;
            }

            log.info("Found {} failed jobs to retry", retryableJobs.size());

            for (ZipProcessingJob job : retryableJobs) {
                job.setStatus(ZipProcessingJob.JobStatus.RETRY);
                jobRepository.save(job);
            }

        } catch (Exception e) {
            log.error("Error retrying failed jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up stuck jobs (jobs that have been processing for too long)
     */
    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void cleanupStuckJobs() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30); // 30 minutes threshold
            List<ZipProcessingJob> stuckJobs = jobRepository.findStuckJobs(cutoffTime);

            if (stuckJobs.isEmpty()) {
                return;
            }

            log.warn("Found {} stuck jobs to reset", stuckJobs.size());

            for (ZipProcessingJob job : stuckJobs) {
                job.setStatus(ZipProcessingJob.JobStatus.PENDING);
                job.setProcessingStartedAt(null);
                job.setAttempts(job.getAttempts() + 1);
                jobRepository.save(job);

                log.warn("Reset stuck job: {} (user: {})", job.getJobId(), job.getUserId());
            }

        } catch (Exception e) {
            log.error("Error cleaning up stuck jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * Get job status for a specific user
     */
    public List<ZipProcessingJob> getUserJobs(Long userId, int page, int size) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(page, size)).getContent();
    }

    /**
     * Create a new ZIP processing job
     */
    public ZipProcessingJob createJob(ZipProcessingJob job) {
        log.info("Creating new job: {} for user: {}", job.getJobId(), job.getUserId());

        try {
            ZipProcessingJob savedJob = jobRepository.save(job);
            log.info("Successfully created job: {} with ID: {}", job.getJobId(), savedJob.getId());
            return savedJob;
        } catch (Exception e) {
            log.error("Failed to create job: {} for user: {}: {}", job.getJobId(), job.getUserId(),
                    e.getMessage(), e);
            throw new RuntimeException("Failed to create job", e);
        }
    }

    /**
     * Get job count by status for a specific user
     */
    public long getUserJobCount(Long userId, ZipProcessingJob.JobStatus status) {
        return jobRepository.countByUserIdAndStatus(userId, status);
    }
}
