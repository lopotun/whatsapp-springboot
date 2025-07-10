package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ChatEntryService {
    
    private final ChatEntryRepository chatEntryRepository;
    
    @Autowired
    public ChatEntryService(ChatEntryRepository chatEntryRepository) {
        this.chatEntryRepository = chatEntryRepository;
    }
    
    /**
     * Save a single chat entry
     */
    public ChatEntryEntity saveChatEntry(ChatEntry chatEntry) {
        ChatEntryEntity entity = ChatEntryEntity.fromChatEntry(chatEntry);
        ChatEntryEntity saved = chatEntryRepository.save(entity);
        log.debug("Saved chat entry: {}", saved.getId());
        return saved;
    }
    
    /**
     * Save multiple chat entries in batch
     */
    public List<ChatEntryEntity> saveChatEntries(List<ChatEntry> chatEntries) {
        List<ChatEntryEntity> entities = chatEntries.stream()
                .map(ChatEntryEntity::fromChatEntry)
                .collect(Collectors.toList());
        
        List<ChatEntryEntity> saved = chatEntryRepository.saveAll(entities);
        log.info("Saved {} chat entries in batch", saved.size());
        return saved;
    }
    
    /**
     * Find chat entry by ID
     */
    public Optional<ChatEntryEntity> findById(Long id) {
        return chatEntryRepository.findById(id);
    }
    
    /**
     * Search chat entries with multiple criteria
     */
    public Page<ChatEntryEntity> searchChatEntries(
            String author,
            ChatEntry.Type type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean hasAttachment,
            int page,
            int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.searchChatEntries(author, type, startDate, endDate, hasAttachment, pageable);
    }
    
    /**
     * Search by keyword in payload and author
     */
    public Page<ChatEntryEntity> searchByKeyword(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.searchByKeyword(keyword, pageable);
    }
    
    /**
     * Advanced search with keyword and other criteria
     */
    public Page<ChatEntryEntity> advancedSearch(
            String keyword,
            String author,
            ChatEntry.Type type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.advancedSearch(keyword, author, type, startDate, endDate, pageable);
    }
    
    /**
     * Find entries by author
     */
    public List<ChatEntryEntity> findByAuthor(String author) {
        return chatEntryRepository.findByAuthor(author);
    }
    
    /**
     * Find entries by type
     */
    public List<ChatEntryEntity> findByType(ChatEntry.Type type) {
        return chatEntryRepository.findByType(type);
    }
    
    /**
     * Find entries by date range
     */
    public List<ChatEntryEntity> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.findByLocalDateTimeBetween(start, end);
    }
    
    /**
     * Find entries by author and type
     */
    public List<ChatEntryEntity> findByAuthorAndType(String author, ChatEntry.Type type) {
        return chatEntryRepository.findByAuthorAndType(author, type);
    }
    
    /**
     * Find entries by author and date range
     */
    public List<ChatEntryEntity> findByAuthorAndDateRange(String author, LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.findByAuthorAndLocalDateTimeBetween(author, start, end);
    }
    
    /**
     * Find entries by type and date range
     */
    public List<ChatEntryEntity> findByTypeAndDateRange(ChatEntry.Type type, LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.findByTypeAndLocalDateTimeBetween(type, start, end);
    }
    
    /**
     * Find entries by attachment hash
     */
    public List<ChatEntryEntity> findByAttachmentHash(String attachmentHash) {
        return chatEntryRepository.findByAttachmentHash(attachmentHash);
    }
    
    /**
     * Get statistics
     */
    public long countByAuthor(String author) {
        return chatEntryRepository.countByAuthor(author);
    }
    
    public long countByType(ChatEntry.Type type) {
        return chatEntryRepository.countByType(type);
    }
    
    public long countByDateRange(LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.countByLocalDateTimeBetween(start, end);
    }
    
    /**
     * Update attachment hash for a chat entry
     */
    public void updateAttachmentHash(Long chatEntryId, String attachmentHash) {
        Optional<ChatEntryEntity> optional = chatEntryRepository.findById(chatEntryId);
        if (optional.isPresent()) {
            ChatEntryEntity entity = optional.get();
            entity.setAttachmentHash(attachmentHash);
            chatEntryRepository.save(entity);
            log.debug("Updated attachment hash for chat entry: {}", chatEntryId);
        } else {
            log.warn("Chat entry not found for ID: {}", chatEntryId);
        }
    }
    
    /**
     * Delete chat entry by ID
     */
    public void deleteById(Long id) {
        chatEntryRepository.deleteById(id);
        log.debug("Deleted chat entry: {}", id);
    }
    
    /**
     * Get all chat entries with pagination
     */
    public Page<ChatEntryEntity> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.findAll(pageable);
    }
} 