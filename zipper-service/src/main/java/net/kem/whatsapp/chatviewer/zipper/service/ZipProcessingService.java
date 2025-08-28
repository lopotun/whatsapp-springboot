package net.kem.whatsapp.chatviewer.zipper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.shared.model.AttachmentEntity;
import net.kem.whatsapp.chatviewer.zipper.model.ProcessingResult;
import net.kem.whatsapp.chatviewer.zipper.model.ZipProcessingJob;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for processing ZIP files containing WhatsApp chat data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZipProcessingService {

    private final AttachmentService attachmentService;
    private final ChatTextProcessingService chatTextProcessingService;
    private final ChatDataService chatDataService;

    /**
     * Process a ZIP file from a job (for compatibility with JobQueueService)
     */
    public ProcessingResult processZipFile(ZipProcessingJob job) throws IOException {
        log.info("Processing ZIP file for job: {} (user: {})", job.getJobId(), job.getUserId());

        // Read the file from the stored path
        Path filePath = Paths.get(job.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("ZIP file not found: " + job.getFilePath());
        }

        try (InputStream zipInputStream = Files.newInputStream(filePath)) {
            return processZipFile(zipInputStream, job.getUserId(), job.getChatId(), job.getJobId());
        }
    }

    /**
     * Process a ZIP file and extract chat data
     */
    public ProcessingResult processZipFile(InputStream zipInputStream, Long userId, String chatId,
            String jobId) throws IOException {
        log.info("Starting ZIP processing for job {} (user: {}, chat: {})", jobId, userId, chatId);

        List<String> extractedFiles = new ArrayList<>();
        List<ProcessingResult.ProcessedAttachment> extractedAttachments = new ArrayList<>();
        List<AttachmentEntity> attachments = new ArrayList<>();
        int totalEntriesProcessed = 0;
        int totalAttachmentsProcessed = 0;

        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            byte[] entryContent = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    extractedFiles.add(entry.getName());

                    if (entry.getName().endsWith(".txt")) {
                        entryContent = readZipEntryContent(zis);
                    } else {
                        Pair<AttachmentEntity, ProcessingResult.ProcessedAttachment> pair =
                            attachmentService.storeFileWithHash(zis, entry.getName(), entry.getSize());
                        attachments.add(pair.getFirst());
                        extractedAttachments.add(pair.getSecond());
                        totalAttachmentsProcessed++;
                    }
                }
                zis.closeEntry();
            }
            // Process chat text file - create a separate stream for this entry
            if(entryContent != null) {
                try (InputStream entryStream = new ByteArrayInputStream(entryContent)) {
                    totalEntriesProcessed =
                        processChatTextFile(entryStream, userId, chatId, jobId, extractedAttachments);
                }
            }
            chatDataService.pendAttachments(userId, chatId, attachments);
            // Commit all changes to the database after processing the entire ZIP
            chatDataService.commitPendingChanges();
        }

        log.info("Completed ZIP processing for job {} (user: {}, chat: {})", jobId, userId, chatId);

        return ProcessingResult.builder().jobId(jobId).chatId(chatId).userId(userId)
                .originalFilename(extractedFiles.isEmpty() ? "" : extractedFiles.getFirst())
                .success(true).processingCompletedAt(LocalDateTime.now())
                .totalEntriesProcessed(totalEntriesProcessed)
                .totalAttachmentsProcessed(totalAttachmentsProcessed).extractedFiles(extractedFiles)
                .attachments(extractedAttachments).build();
    }

    /**
     * Process chat text file from ZIP
     */
    private int processChatTextFile(InputStream chatTextStream, Long userId, String chatId,
            String jobId, List<ProcessingResult.ProcessedAttachment> processedAttachments) throws IOException {
        log.info("Processing chat text file for job {} (user: {}, chat: {})", jobId, userId, chatId);

        // Process the chat text and get chat entries
        List<ChatEntry> chatEntries = chatTextProcessingService.processChatTextStream(chatTextStream, userId, chatId);

        // Get filename to checksum mapping for attachment linking
        Map<String, String> filenameToChecksum = processedAttachments.stream().collect(Collectors.toMap(
            ProcessingResult.ProcessedAttachment::getOriginalFilename,
            ProcessingResult.ProcessedAttachment::getContentHash));

        log.info("Processed {} chat entries for job {} (user: {}, chat: {})",
            chatEntries.size(), jobId, userId, chatId);

        // Actually save chat entries to the local database

        try {
            chatDataService.pendChatEntries(userId, chatId, chatEntries, filenameToChecksum);
            log.info(
                    "Successfully saved {} chat entries to local database for job {} (user: {}, chat: {})",
                    chatEntries.size(), jobId, userId, chatId);
        } catch (Exception e) {
            log.error(
                    "Failed to save chat entries to local database for job {} (user: {}, chat: {}): {}",
                    jobId, userId, chatId, e.getMessage(), e);
            // Don't throw the exception to avoid failing the entire job, just log the error
        }

        return chatEntries.size();
    }

    /**
     * Read the content of a ZIP entry into a byte array
     */
    private byte[] readZipEntryContent(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

}
