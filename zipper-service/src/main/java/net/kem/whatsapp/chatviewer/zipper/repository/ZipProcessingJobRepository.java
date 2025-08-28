package net.kem.whatsapp.chatviewer.zipper.repository;

import net.kem.whatsapp.chatviewer.zipper.model.ZipProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZipProcessingJobRepository extends JpaRepository<ZipProcessingJob, Long> {

    /**
     * Find jobs by user ID with pagination
     */
    Page<ZipProcessingJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Find pending jobs ordered by priority and creation time
     */
    @Query("SELECT j FROM ZipProcessingJob j WHERE j.status = 'PENDING' ORDER BY j.priority DESC, j.createdAt ASC")
    List<ZipProcessingJob> findPendingJobsOrderedByPriority();

    /**
     * Find active (pending or retry) jobs ordered by priority and creation time
     */
    @Query("SELECT j FROM ZipProcessingJob j WHERE j.status IN ('PENDING', 'RETRY') ORDER BY j.priority DESC, j.createdAt ASC")
    List<ZipProcessingJob> findActiveJobsOrderedByPriority();

    /**
     * Find jobs by status for a specific user
     */
    List<ZipProcessingJob> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId,
            ZipProcessingJob.JobStatus status);

    /**
     * Find job by job ID and user ID (ensures user isolation)
     */
    Optional<ZipProcessingJob> findByJobIdAndUserId(String jobId, Long userId);

    /**
     * Count jobs by status for a specific user
     */
    long countByUserIdAndStatus(Long userId, ZipProcessingJob.JobStatus status);

    /**
     * Find failed jobs that can be retried
     */
    @Query("SELECT j FROM ZipProcessingJob j WHERE j.status = 'FAILED' AND j.attempts < j.maxAttempts ORDER BY j.createdAt ASC")
    List<ZipProcessingJob> findRetryableFailedJobs();

    /**
     * Find jobs that have been processing for too long (stuck jobs)
     */
    @Query("SELECT j FROM ZipProcessingJob j WHERE j.status = 'PROCESSING' AND j.processingStartedAt < :cutoffTime")
    List<ZipProcessingJob> findStuckJobs(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);
}
