package net.kem.whatsapp.chatviewer.zipper.controller;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.zipper.model.ZipProcessingJob;
import net.kem.whatsapp.chatviewer.zipper.service.JobQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobQueueService jobQueueService;

    /**
     * Get jobs for a specific user with pagination
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ZipProcessingJob>> getUserJobs(@PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Getting jobs for user: {} (page: {}, size: {})", userId, page, size);

        List<ZipProcessingJob> jobs = jobQueueService.getUserJobs(userId, page, size);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get job count by status for a specific user
     */
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<JobCountResponse> getUserJobCount(@PathVariable Long userId,
            @RequestParam(required = false) String status) {

        log.info("Getting job count for user: {} with status: {}", userId, status);

        if (status != null) {
            try {
                ZipProcessingJob.JobStatus jobStatus =
                        ZipProcessingJob.JobStatus.valueOf(status.toUpperCase());
                long count = jobQueueService.getUserJobCount(userId, jobStatus);
                return ResponseEntity.ok(new JobCountResponse(userId, status, count));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            // Return counts for all statuses
            long pendingCount =
                    jobQueueService.getUserJobCount(userId, ZipProcessingJob.JobStatus.PENDING);
            long processingCount =
                    jobQueueService.getUserJobCount(userId, ZipProcessingJob.JobStatus.PROCESSING);
            long completedCount =
                    jobQueueService.getUserJobCount(userId, ZipProcessingJob.JobStatus.COMPLETED);
            long failedCount =
                    jobQueueService.getUserJobCount(userId, ZipProcessingJob.JobStatus.FAILED);

            return ResponseEntity.ok(new JobCountResponse(userId, pendingCount, processingCount,
                    completedCount, failedCount));
        }
    }

    /**
     * Create a new ZIP processing job
     */
    @PostMapping
    public ResponseEntity<String> createJob(@RequestBody ZipProcessingJobRequest request) {
        log.info("Creating new ZIP processing job: {} for user: {}", request.getJobId(),
                request.getUserId());

        try {
            // Create the job entity
            ZipProcessingJob job = ZipProcessingJob.builder().jobId(request.getJobId())
                    .userId(request.getUserId()).chatId(request.getChatId())
                    .originalFilename(request.getOriginalFilename()).filePath(request.getFilePath())
                    .fileSize(request.getFileSize()).status(ZipProcessingJob.JobStatus.PENDING)
                    .priority(1).maxAttempts(3).build();

            // Save the job
            jobQueueService.createJob(job);

            log.info("Successfully created job: {} for user: {}", request.getJobId(),
                    request.getUserId());
            return ResponseEntity.ok("Job created successfully: " + request.getJobId());

        } catch (Exception e) {
            log.error("Failed to create job: {} for user: {}: {}", request.getJobId(),
                    request.getUserId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to create job: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Zipper service is running");
    }

    /**
     * Response DTO for job counts
     */
    @Getter
    public static class JobCountResponse {
        // Getters
        private final Long userId;
        private final String status;
        private final Long count;
        private final Long pendingCount;
        private final Long processingCount;
        private final Long completedCount;
        private final Long failedCount;

        // Constructor for single status count
        public JobCountResponse(Long userId, String status, Long count) {
            this.userId = userId;
            this.status = status;
            this.count = count;
            this.pendingCount = null;
            this.processingCount = null;
            this.completedCount = null;
            this.failedCount = null;
        }

        // Constructor for all status counts
        public JobCountResponse(Long userId, Long pendingCount, Long processingCount,
                Long completedCount, Long failedCount) {
            this.userId = userId;
            this.status = null;
            this.count = null;
            this.pendingCount = pendingCount;
            this.processingCount = processingCount;
            this.completedCount = completedCount;
            this.failedCount = failedCount;
        }

    }

    /**
     * Request DTO for creating ZIP processing jobs
     */
    @Data
    public static class ZipProcessingJobRequest {
        // Getters and setters
        private String jobId;
        private Long userId;
        private String chatId;
        private String originalFilename;
        private String filePath;
        private Long fileSize;

    }
}
