package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/chat-entries")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatEntryController {
    
    private final ChatEntryService chatEntryService;
    private final UserService userService;
    
    /**
     * Get current user ID from authentication
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userService.findByUsername(username)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    /**
     * Get chat entry by ID (user-specific)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatEntryEntity> getChatEntry(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        Optional<ChatEntryEntity> chatEntry = chatEntryService.findById(id, userId);
        return chatEntry.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Boolean hasAttachment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Long userId = getCurrentUserId();
        Page<ChatEntryEntity> results = chatEntryService.searchChatEntries(
                userId, author, type, startDate, endDate, hasAttachment, page, size);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Search by keyword in payload and author (user-specific)
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<ChatEntryEntity>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Long userId = getCurrentUserId();
        Page<ChatEntryEntity> results = chatEntryService.searchByKeyword(userId, keyword, page, size);
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<ChatEntryEntity> results = chatEntryService.advancedSearch(
                keyword, author, type, startDate, endDate, page, size);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Find entries by author
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<List<ChatEntryEntity>> findByAuthor(@PathVariable String author) {
        List<ChatEntryEntity> results = chatEntryService.findByAuthor(author);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Find entries by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<ChatEntryEntity>> findByType(@PathVariable ChatEntry.Type type) {
        List<ChatEntryEntity> results = chatEntryService.findByType(type);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Find entries by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<ChatEntryEntity>> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        List<ChatEntryEntity> results = chatEntryService.findByDateRange(start, end);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Find entries by author and type
     */
    @GetMapping("/author/{author}/type/{type}")
    public ResponseEntity<List<ChatEntryEntity>> findByAuthorAndType(
            @PathVariable String author,
            @PathVariable ChatEntry.Type type) {
        
        List<ChatEntryEntity> results = chatEntryService.findByAuthorAndType(author, type);
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
        
        List<ChatEntryEntity> results = chatEntryService.findByAuthorAndDateRange(author, start, end);
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
        
        List<ChatEntryEntity> results = chatEntryService.findByTypeAndDateRange(type, start, end);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Find entries by attachment hash
     */
    @GetMapping("/attachment/{hash}")
    public ResponseEntity<List<ChatEntryEntity>> findByAttachmentHash(@PathVariable String hash) {
        List<ChatEntryEntity> results = chatEntryService.findByAttachmentHash(hash);
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
     * Update attachment hash for a chat entry
     */
    @PutMapping("/{id}/attachment")
    public ResponseEntity<Void> updateAttachmentHash(
            @PathVariable Long id,
            @RequestParam String attachmentHash) {
        
        chatEntryService.updateAttachmentHash(id, attachmentHash);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Delete chat entry by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatEntry(@PathVariable Long id) {
        chatEntryService.deleteById(id);
        return ResponseEntity.ok().build();
    }
} 