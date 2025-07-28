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
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;
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
     * Get all locations for a client
     */
    @GetMapping("/locations/user/{userId}")
    public ResponseEntity<List<Location>> getLocationsByUserId(@PathVariable Long userId) {
        List<Location> locations = attachmentService.findLocationsByUserId(userId);
        return ResponseEntity.ok(locations);
    }

    /**
     * Get locations for a client with specific status
     */
    @GetMapping("/locations/user/{userId}/status/{status}")
    public ResponseEntity<List<Location>> getLocationsByUserIdAndStatus(@PathVariable Long userId,
            @PathVariable Byte status) {
        List<Location> locations = attachmentService.findLocationsByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(locations);
    }



    /**
     * Get all attachments for the current user
     */
    @GetMapping
    public ResponseEntity<List<Attachment>> getUserAttachments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return userService.findByUsername(username).map(user -> {
            // Get attachment IDs for the user using the new user_id field
            List<Long> attachmentIds = attachmentService.findAttachmentIdsByUserId(user.getId());

            // Fetch attachments by IDs
            List<Attachment> userAttachments = attachmentIds.stream()
                    .map(attachmentId -> attachmentService.findById(attachmentId))
                    .filter(java.util.Optional::isPresent).map(java.util.Optional::get).toList();

            return ResponseEntity.ok(userAttachments);
        }).orElse(ResponseEntity.notFound().build());
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
     * Get locations updated after a specific timestamp
     */
    @GetMapping("/locations/after/{timestamp}")
    public ResponseEntity<List<Location>> getLocationsAfterTimestamp(
            @PathVariable String timestamp) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp);
            List<Location> locations = attachmentService.findLocationsAfterTimestamp(dateTime);
            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            log.error("Invalid timestamp format: {}", timestamp, e);
            return ResponseEntity.badRequest().build();
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

    /**
     * Update location status
     */
    @PutMapping("/locations/{locationId}/status/{status}")
    public ResponseEntity<Location> updateLocationStatus(@PathVariable Long locationId,
            @PathVariable Byte status) {
        try {
            Location location = attachmentService.updateLocationStatus(locationId, status);
            return ResponseEntity.ok(location);
        } catch (IllegalArgumentException e) {
            log.error("Location not found: {}", locationId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
