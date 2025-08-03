package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.LocationRepository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.AttachmentService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService userService;
    private final ChatEntryService chatEntryService;
    private final LocationRepository locationRepository;

    public AttachmentController(@Autowired AttachmentService attachmentService,
            @Autowired UserService userService, @Autowired ChatEntryService chatEntryService,
            @Autowired LocationRepository locationRepository) {
        this.attachmentService = attachmentService;
        this.userService = userService;
        this.chatEntryService = chatEntryService;
        this.locationRepository = locationRepository;
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

    /**
     * Get current user ID from security context
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userService.findByUsername(username).map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    /**
     * Fix location records by linking them to chat entry records
     */
    @PostMapping("/fix-locations")
    public ResponseEntity<Map<String, Object>> fixLocations() {
        Long userId = getCurrentUserId();
        try {
            List<Location> locations = attachmentService.findLocationsByUserId(userId);
            int fixedCount = 0;

            log.info("Found {} locations to check", locations.size());

            // Get all chat entries using pagination
            List<ChatEntryEntity> allChatEntries = new ArrayList<>();
            int page = 0;
            int size = 100;
            boolean hasMore = true;

            while (hasMore) {
                Page<ChatEntryEntity> pageResult =
                        chatEntryService.findByUserId(userId, page, size);
                allChatEntries.addAll(pageResult.getContent());
                hasMore = pageResult.hasNext();
                page++;
            }

            log.info("Retrieved {} total chat entries", allChatEntries.size());

            for (Location location : locations) {
                if (location.getChatEntry() == null && location.getRealFilename() != null) {
                    log.debug("Checking location {} with filename: {}", location.getId(),
                            location.getRealFilename());

                    // Find chat entry with this filename
                    List<ChatEntryEntity> matchingEntries = allChatEntries.stream()
                            .filter(entry -> location.getRealFilename().equals(entry.getFileName()))
                            .collect(Collectors.toList());

                    log.debug("Found {} matching chat entries for filename: {}",
                            matchingEntries.size(), location.getRealFilename());

                    if (!matchingEntries.isEmpty()) {
                        ChatEntryEntity chatEntry = matchingEntries.get(0);
                        location.setChatEntry(chatEntry);
                        locationRepository.save(location);
                        fixedCount++;
                        log.info("Fixed location {} for chat entry {} with filename: {}",
                                location.getId(), chatEntry.getId(), location.getRealFilename());
                    } else {
                        log.warn("No matching chat entry found for location {} with filename: {}",
                                location.getId(), location.getRealFilename());
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Fixed " + fixedCount + " location records");
            response.put("fixedCount", fixedCount);
            response.put("totalLocations", locations.size());
            response.put("totalChatEntries", allChatEntries.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fixing locations: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to fix locations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
