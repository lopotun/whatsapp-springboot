package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/chat-entries")
@CrossOrigin(origins = "*")
public class ChatEntryController {
    
    private final ChatEntryService chatEntryService;
    
    @Autowired
    public ChatEntryController(ChatEntryService chatEntryService) {
        this.chatEntryService = chatEntryService;
    }
    
    /**
     * Get chat entry by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChatEntryEntity> getChatEntry(@PathVariable Long id) {
        Optional<ChatEntryEntity> chatEntry = chatEntryService.findById(id);
        return chatEntry.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get all chat entries with pagination
     */
    @GetMapping
    public ResponseEntity<Page<ChatEntryEntity>> getAllChatEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<ChatEntryEntity> chatEntries = chatEntryService.findAll(page, size);
        return ResponseEntity.ok(chatEntries);
    }
    
    /**
     * Search chat entries with multiple criteria
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
        
        Page<ChatEntryEntity> results = chatEntryService.searchChatEntries(
                author, type, startDate, endDate, hasAttachment, page, size);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Search by keyword in payload and author
     */
    @GetMapping("/search/keyword")
    public ResponseEntity<Page<ChatEntryEntity>> searchByKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<ChatEntryEntity> results = chatEntryService.searchByKeyword(keyword, page, size);
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
     * Get statistics
     */
    @GetMapping("/stats/author/{author}")
    public ResponseEntity<Long> countByAuthor(@PathVariable String author) {
        long count = chatEntryService.countByAuthor(author);
        return ResponseEntity.ok(count);
    }
    
    @GetMapping("/stats/type/{type}")
    public ResponseEntity<Long> countByType(@PathVariable ChatEntry.Type type) {
        long count = chatEntryService.countByType(type);
        return ResponseEntity.ok(count);
    }
    
    @GetMapping("/stats/date-range")
    public ResponseEntity<Long> countByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        long count = chatEntryService.countByDateRange(start, end);
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