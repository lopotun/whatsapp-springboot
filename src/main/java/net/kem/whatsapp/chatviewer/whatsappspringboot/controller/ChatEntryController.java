package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.AttachmentService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat-entries")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatEntryController {

    private final ChatEntryService chatEntryService;
    private final UserService userService;
    private final AttachmentService attachmentService;

    /**
     * Get current user ID from authentication
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();

        // Try to find user by username first (traditional auth)
        Optional<User> userOpt = userService.findByUsername(principalName);

        // If not found, try to find by OAuth2 ID (OAuth2 auth)
        if (userOpt.isEmpty() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthToken.getName();
            userOpt = userService.findByOauthProviderAndOauthId(provider, oauthId);
        }

        return userOpt.map(User::getId).orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Get chat entry by ID (user-specific)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatEntryEntity> getChatEntry(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        Optional<ChatEntryEntity> chatEntry = chatEntryService.findById(id, userId);
        return chatEntry.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all chat entries with pagination (user-specific)
     */
    @GetMapping
    public ResponseEntity<Page<ChatEntryEntity>> getAllChatEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = getCurrentUserId();
        Page<ChatEntryEntity> chatEntries = chatEntryService.findByUserId(userId, page, size);
        return ResponseEntity.ok(chatEntries);
    }

    /**
     * Search chat entries with multiple criteria (user-specific)
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ChatEntryEntity>> searchChatEntries(
            @RequestParam(required = false) String author,
            @RequestParam(required = false) ChatEntry.Type type,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Boolean hasAttachment,
            @RequestParam(required = false) List<String> chatIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = getCurrentUserId();
        log.debug("Search request - user: {}, author: {}, type: {}, page: {}, size: {}", userId,
                author, type, page, size);

        Page<ChatEntryEntity> results = chatEntryService.searchChatEntries(userId, author, type,
                startDate, endDate, hasAttachment, chatIds, page, size);

        log.debug("Search results - total: {}, content size: {}, page: {}",
                results.getTotalElements(), results.getContent().size(), results.getNumber());

        return ResponseEntity.ok(results);
    }

    /**
     * Search by keyword in payload and author (user-specific)
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<ChatEntryEntity>> searchByKeyword(@RequestParam String keyword,
            @RequestParam(required = false) List<String> chatIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = getCurrentUserId();
        log.info(
                "Keyword search request - keyword: '{}', chatIds: {}, page: {}, size: {} for user: {}",
                keyword, chatIds, page, size, userId);

        Page<ChatEntryEntity> results =
                chatEntryService.searchByKeyword(userId, keyword, chatIds, page, size);

        log.info("Keyword search response - total elements: {}, total pages: {}",
                results.getTotalElements(), results.getTotalPages());

        return ResponseEntity.ok(results);
    }

    /**
     * Advanced search with keyword and other criteria
     */
    @GetMapping("/search/advanced")
    public ResponseEntity<Page<ChatEntryEntity>> advancedSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) ChatEntry.Type type,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) List<String> chatIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = getCurrentUserId();
        log.debug(
                "Advanced search request - user: {}, keyword: {}, author: {}, type: {}, page: {}, size: {}",
                userId, keyword, author, type, page, size);

        Page<ChatEntryEntity> results = chatEntryService.advancedSearch(userId, keyword, author,
                type, startDate, endDate, chatIds, page, size);

        log.debug("Advanced search results - total: {}, content size: {}, page: {}",
                results.getTotalElements(), results.getContent().size(), results.getNumber());

        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by author
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<List<ChatEntryEntity>> findByAuthor(@PathVariable String author) {
        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results = chatEntryService.findByAuthor(userId, author);
        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<ChatEntryEntity>> findByType(@PathVariable ChatEntry.Type type) {
        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results = chatEntryService.findByType(userId, type);
        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<ChatEntryEntity>> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results = chatEntryService.findByDateRange(userId, start, end);
        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by author and type
     */
    @GetMapping("/author/{author}/type/{type}")
    public ResponseEntity<List<ChatEntryEntity>> findByAuthorAndType(@PathVariable String author,
            @PathVariable ChatEntry.Type type) {

        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results = chatEntryService.findByAuthorAndType(userId, author, type);
        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by author and date range
     */
    @GetMapping("/author/{author}/date-range")
    public ResponseEntity<List<ChatEntryEntity>> findByAuthorAndDateRange(
            @PathVariable String author,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results =
                chatEntryService.findByAuthorAndDateRange(userId, author, start, end);
        return ResponseEntity.ok(results);
    }

    /**
     * Find entries by type and date range
     */
    @GetMapping("/type/{type}/date-range")
    public ResponseEntity<List<ChatEntryEntity>> findByTypeAndDateRange(
            @PathVariable ChatEntry.Type type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Long userId = getCurrentUserId();
        List<ChatEntryEntity> results =
                chatEntryService.findByTypeAndDateRange(userId, type, start, end);
        return ResponseEntity.ok(results);
    }

    /**
     * Get statistics (user-specific)
     */
    @GetMapping("/stats/author/{author}")
    public ResponseEntity<Long> countByAuthor(@PathVariable String author) {
        Long userId = getCurrentUserId();
        long count = chatEntryService.countByAuthor(userId, author);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/stats/type/{type}")
    public ResponseEntity<Long> countByType(@PathVariable ChatEntry.Type type) {
        Long userId = getCurrentUserId();
        long count = chatEntryService.countByType(userId, type);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/stats/date-range")
    public ResponseEntity<Long> countByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Long userId = getCurrentUserId();
        long count = chatEntryService.countByDateRange(userId, start, end);
        return ResponseEntity.ok(count);
    }

    /**
     * Delete chat entry by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatEntry(@PathVariable Long id) {
        chatEntryService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Get all chat IDs for the current user
     */
    @GetMapping("/chats")
    public ResponseEntity<List<String>> getUserChats() {
        Long userId = getCurrentUserId();
        List<String> chatIds = chatEntryService.getChatIdsForUser(userId);
        return ResponseEntity.ok(chatIds);
    }

    /**
     * Download attachment for a chat entry (user-specific)
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        Optional<ChatEntryEntity> chatEntry = chatEntryService.findById(id, userId);

        if (chatEntry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ChatEntryEntity entry = chatEntry.get();
        if (entry.getFileName() == null || entry.getFileName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Resource resource = chatEntryService.downloadAttachment(entry.getId(), userId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + entry.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);
        } catch (Exception e) {
            log.error("Error downloading attachment for chat entry {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * View attachment for a chat entry (user-specific)
     */
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewAttachment(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        Optional<ChatEntryEntity> chatEntry = chatEntryService.findById(id, userId);

        if (chatEntry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ChatEntryEntity entry = chatEntry.get();
        if (entry.getFileName() == null || entry.getFileName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Resource resource = chatEntryService.downloadAttachment(entry.getId(), userId);

            // Determine content type based on file extension
            String contentType = determineContentType(entry.getFileName());

            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            log.error("Error viewing attachment for chat entry {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Determine content type based on file extension
     */
    private String determineContentType(String fileName) {
        if (fileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFileName.endsWith(".avi")) {
            return "video/x-msvideo";
        } else if (lowerFileName.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerFileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerFileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) {
            return "application/msword";
        } else {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
