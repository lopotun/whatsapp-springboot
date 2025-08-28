package net.kem.whatsapp.chatviewer.zipper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.zipper.model.ProcessingResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /**
     * Log ZIP processing completion (no longer notifying main service)
     */
    public void notifyMainService(ProcessingResult result) {
        log.info("ZIP processing completed for job: {} (user: {}) - {} entries, {} attachments",
                result.getJobId(), result.getUserId(), result.getTotalEntriesProcessed(),
                result.getTotalAttachmentsProcessed());

        if (!result.isSuccess()) {
            log.warn("Processing failed for job: {} (user: {}) - {}", result.getJobId(),
                    result.getUserId(), result.getErrorMessage());
        }
    }

    /**
     * Send email notification to user about processing completion
     */
    public void sendUserNotification(ProcessingResult result) {
        // This could integrate with an email service (SendGrid, AWS SES, etc.)
        // For now, we'll just log it
        if (result.isSuccess()) {
            log.info("Processing completed successfully for user: {} - {} entries, {} attachments",
                    result.getUserId(), result.getTotalEntriesProcessed(),
                    result.getTotalAttachmentsProcessed());
        } else {
            log.warn("Processing failed for user: {} - {}", result.getUserId(),
                    result.getErrorMessage());
        }
    }
}
