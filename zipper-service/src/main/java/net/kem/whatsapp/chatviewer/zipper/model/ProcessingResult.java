package net.kem.whatsapp.chatviewer.zipper.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    private String jobId;
    private String chatId;
    private Long userId;
    private String originalFilename;
    private boolean success;
    private String errorMessage;
    private LocalDateTime processingCompletedAt;
    private Integer totalEntriesProcessed;
    private Integer totalAttachmentsProcessed;
    private List<String> extractedFiles;
    private List<ProcessedAttachment> attachments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedAttachment {
        private String originalFilename;
        private String contentHash;
        private String filePath;
        private Long fileSize;
        private String mimeType;
    }
}
