package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.AttachmentService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService userService;

    public AttachmentController(@Autowired AttachmentService attachmentService,
            @Autowired UserService userService) {
        this.attachmentService = attachmentService;
        this.userService = userService;
    }

    /**
     * Get attachment by hash
     */
    @GetMapping("/hash/{hash}")
    public ResponseEntity<Attachment> getAttachmentByHash(@PathVariable String hash) {
        Optional<Attachment> attachment = attachmentService.findByHash(hash);
        return attachment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if attachment exists by hash
     */
    @GetMapping("/hash/{hash}/exists")
    public ResponseEntity<Boolean> checkAttachmentExists(@PathVariable String hash) {
        boolean exists = attachmentService.existsByHash(hash);
        return ResponseEntity.ok(exists);
    }

    /**
     * Get all attachments
     */
    @GetMapping
    public ResponseEntity<List<Attachment>> getAllAttachments() {
        List<Attachment> attachments = attachmentService.findAllAttachments();
        return ResponseEntity.ok(attachments);
    }

    /**
     * Get all attachments with specific status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Attachment>> getAttachmentsByStatus(@PathVariable Byte status) {
        List<Attachment> attachments = attachmentService.findAttachmentsByStatus(status);
        return ResponseEntity.ok(attachments);
    }

    /**
     * Get attachments created after a specific timestamp
     */
    @GetMapping("/after/{timestamp}")
    public ResponseEntity<List<Attachment>> getAttachmentsAfterTimestamp(
            @PathVariable String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp);
            List<Attachment> attachments =
                    attachmentService.findAttachmentsAfterTimestamp(dateTime);
            return ResponseEntity.ok(attachments);
        } catch (Exception e) {
            log.error("Invalid timestamp format: {}", timestamp, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get attachments by file size range
     */
    @GetMapping("/size/range/{minSize}/{maxSize}")
    public ResponseEntity<List<Attachment>> getAttachmentsByFileSizeRange(
            @PathVariable Long minSize, @PathVariable Long maxSize) {
        List<Attachment> attachments =
                attachmentService.findAttachmentsByFileSizeRange(minSize, maxSize);
        return ResponseEntity.ok(attachments);
    }

    /**
     * Get attachments larger than specified size
     */
    @GetMapping("/size/larger/{size}")
    public ResponseEntity<List<Attachment>> getAttachmentsLargerThan(@PathVariable Long size) {
        List<Attachment> attachments = attachmentService.findAttachmentsLargerThan(size);
        return ResponseEntity.ok(attachments);
    }

    /**
     * Get attachments smaller than specified size
     */
    @GetMapping("/size/smaller/{size}")
    public ResponseEntity<List<Attachment>> getAttachmentsSmallerThan(@PathVariable Long size) {
        List<Attachment> attachments = attachmentService.findAttachmentsSmallerThan(size);
        return ResponseEntity.ok(attachments);
    }

    /**
     * Get total file size for all attachments
     */
    @GetMapping("/stats/total-size")
    public ResponseEntity<Long> getTotalFileSize() {
        Long totalSize = attachmentService.getTotalFileSize();
        return ResponseEntity.ok(totalSize);
    }

    /**
     * Get total file size for attachments by status
     */
    @GetMapping("/stats/total-size/status/{status}")
    public ResponseEntity<Long> getTotalFileSizeByStatus(@PathVariable Byte status) {
        Long totalSize = attachmentService.getTotalFileSizeByStatus(status);
        return ResponseEntity.ok(totalSize);
    }

    /**
     * Get total file size for current user's attachments
     */
    @GetMapping("/stats/user/total-size")
    public ResponseEntity<Long> getCurrentUserTotalFileSize() {
        try {
            // Get current user ID from authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            Long userId = userService.findByUsername(username).map(User::getId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Long totalSize = attachmentService.getTotalFileSizeForUser(userId);
            return ResponseEntity.ok(totalSize);
        } catch (Exception e) {
            log.error("Error getting total file size for user: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update attachment status
     */
    @PutMapping("/hash/{hash}/status/{status}")
    public ResponseEntity<Attachment> updateAttachmentStatus(@PathVariable String hash,
            @PathVariable Byte status) {
        try {
            Attachment attachment = attachmentService.updateAttachmentStatus(hash, status);
            return ResponseEntity.ok(attachment);
        } catch (IllegalArgumentException e) {
            log.error("Attachment not found: {}", hash, e);
            return ResponseEntity.notFound().build();
        }
    }
}
