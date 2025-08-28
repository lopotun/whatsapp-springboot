package net.kem.whatsapp.chatviewer.frontend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.frontend.service.AttachmentService;
import net.kem.whatsapp.chatviewer.frontend.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.frontend.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Internal API controller for communication between services This handles requests from the zipper
 * service
 */
@RestController
@RequestMapping("/api/internal/chat-data")
@RequiredArgsConstructor
@Slf4j
public class InternalApiController {

    private final ChatEntryService chatEntryService;
    private final ChatService chatService;
    private final AttachmentService attachmentService;

    /**
     * Save chat entries with attachment linking
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveChatEntries(@RequestBody ChatDataRequest request) {
        log.info("Received request to save {} chat entries for user {} and chat {}",
                request.getChatEntries().size(), request.getUserId(), request.getChatId());

        try {
            // Save chat entries using the shared model method
            List<ChatEntryEntity> savedEntries = chatEntryService.saveSharedChatEntries(
                    request.getChatEntries(), request.getUserId(), request.getChatId());

            // Link attachments to chat entries
            linkAttachmentsToEntries(savedEntries, request.getFilenameToChecksum());

            log.info("Successfully saved {} chat entries for user {} and chat {}",
                    savedEntries.size(), request.getUserId(), request.getChatId());

            return ResponseEntity.ok("Successfully saved " + savedEntries.size() + " chat entries");

        } catch (Exception e) {
            log.error("Failed to save chat entries for user {} and chat {}: {}",
                    request.getUserId(), request.getChatId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to save chat entries: " + e.getMessage());
        }
    }

    /**
     * Update a specific chat entry with attachment information
     */
    @PostMapping("/update-attachment")
    public ResponseEntity<String> updateChatEntryAttachment(
            @RequestBody AttachmentUpdateRequest request) {
        log.debug("Received request to update chat entry {} with attachment hash {}",
                request.getChatEntryId(), request.getAttachmentHash());

        try {
            // Find the attachment by hash
            attachmentService.findByHash(request.getAttachmentHash()).ifPresent(attachment -> {
                // Update the chat entry with the attachment
                chatEntryService.updateChatEntryAttachment(Long.valueOf(request.getChatEntryId()),
                        attachment);
                log.debug("Successfully linked attachment {} to chat entry {}",
                        request.getAttachmentHash(), request.getChatEntryId());
            });

            return ResponseEntity.ok("Attachment updated successfully");

        } catch (Exception e) {
            log.error("Failed to update chat entry {} with attachment {}: {}",
                    request.getChatEntryId(), request.getAttachmentHash(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to update attachment: " + e.getMessage());
        }
    }

    /**
     * Check if a chat entry already exists
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> chatEntryExists(@RequestParam Long userId,
            @RequestParam String timestamp, @RequestParam String author,
            @RequestParam String payload) {
        log.debug("Checking if chat entry exists for user {}: {} - {} - {}", userId, timestamp,
                author, payload);

        try {
            // Parse timestamp to LocalDateTime
            LocalDateTime localDateTime = LocalDateTime.parse(timestamp);

            // Check if entry exists
            boolean exists = chatEntryService.existsByUniqueFields(userId, "temp", localDateTime,
                    author, payload);

            return ResponseEntity.ok(exists);

        } catch (Exception e) {
            log.error("Failed to check if chat entry exists for user {}: {} - {} - {}: {}", userId,
                    timestamp, author, payload, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(false);
        }
    }

    /**
     * Get chat entries for a specific user and chat
     */
    @GetMapping("/entries")
    public ResponseEntity<List<ChatEntry>> getChatEntries(@RequestParam Long userId,
            @RequestParam String chatId) {
        log.debug("Received request to get chat entries for user {} and chat {}", userId, chatId);

        try {
            List<ChatEntryEntity> entities = chatEntryService.findByUserIdAndChatId(userId, chatId);

            // Convert to shared ChatEntry model
            List<ChatEntry> entries = entities.stream()
                    .map(entity -> ChatEntry.builder().localDateTime(entity.getLocalDateTime())
                            .author(entity.getAuthor()).payload(entity.getPayload())
                            .type(entity.getType() != null ? entity.getType() : ChatEntry.Type.UNKNOWN)
                            .fileName(entity.getFileName()).build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(entries);

        } catch (Exception e) {
            log.error("Failed to get chat entries for user {} and chat {}: {}", userId, chatId,
                    e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Link attachments to chat entries based on filename mapping
     */
    private void linkAttachmentsToEntries(List<ChatEntryEntity> entries,
            Map<String, String> filenameToChecksum) {
        for (ChatEntryEntity entry : entries) {
            if (entry.getFileName() != null
                    && filenameToChecksum.containsKey(entry.getFileName())) {
                String hash = filenameToChecksum.get(entry.getFileName());

                // Find the attachment by hash
                attachmentService.findByHash(hash).ifPresent(attachment -> {
                    entry.setAttachment(attachment);
                    entry.setPath(attachmentService.generateFilePath(hash));

                    // Save the updated entity
                    chatEntryService.saveChatEntry(entry);

                    log.debug("Linked attachment {} to chat entry {} with filename {}", hash,
                            entry.getId(), entry.getFileName());
                });
            }
        }
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChatDataRequest {
        private List<ChatEntry> chatEntries;
        private Long userId;
        private String chatId;
        private Map<String, String> filenameToChecksum;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AttachmentUpdateRequest {
        private String chatEntryId;
        private String attachmentHash;
    }
}
