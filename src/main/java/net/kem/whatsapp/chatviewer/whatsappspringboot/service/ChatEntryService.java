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
     * Save a single chat entry with user and chat association
     */
    public ChatEntryEntity saveChatEntry(ChatEntry chatEntry, Long userId, String chatId) {
        ChatEntryEntity entity = ChatEntryEntity.fromChatEntry(chatEntry, userId, chatId);
        ChatEntryEntity saved = chatEntryRepository.save(entity);
        log.debug("Saved chat entry: {} for user: {} and chat: {}", saved.getId(), userId, chatId);
        return saved;
    }
    
    /**
     * Save multiple chat entries in batch with user and chat association
     */
    public List<ChatEntryEntity> saveChatEntries(List<ChatEntry> chatEntries, Long userId, String chatId) {
        List<ChatEntryEntity> entities = chatEntries.stream()
                .map(chatEntry -> ChatEntryEntity.fromChatEntry(chatEntry, userId, chatId))
                .collect(Collectors.toList());
        
        List<ChatEntryEntity> saved = chatEntryRepository.saveAll(entities);
        log.info("Saved {} chat entries in batch for user: {} and chat: {}", saved.size(), userId, chatId);
        return saved;
    }
    
    /**
     * Find chat entry by ID (user-specific)
     */
    public Optional<ChatEntryEntity> findById(Long id, Long userId) {
        return chatEntryRepository.findById(id)
                .filter(entry -> entry.getUserId().equals(userId));
    }
    
    /**
     * Get all chat entries for a user
     */
    public Page<ChatEntryEntity> findByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.findByUserId(userId, pageable);
    }
    
    /**
     * Get all chat entries for a specific chat of a user
     */
    public Page<ChatEntryEntity> findByUserIdAndChatId(Long userId, String chatId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.findByUserIdAndChatId(userId, chatId, pageable);
    }
    
    /**
     * Get all chat IDs for a user
     */
    public List<String> getChatIdsForUser(Long userId) {
        return chatEntryRepository.findDistinctChatIdsByUserId(userId);
    }
    
    /**
     * Delete all entries for a specific chat of a user
     */
    public void deleteChat(Long userId, String chatId) {
        chatEntryRepository.deleteByUserIdAndChatId(userId, chatId);
        log.info("Deleted all entries for chat: {} of user: {}", chatId, userId);
    }
    
    /**
     * Search chat entries with multiple criteria (user-specific)
     */
    public Page<ChatEntryEntity> searchChatEntries(
            Long userId,
            String author,
            ChatEntry.Type type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean hasAttachment,
            int page,
            int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatEntryEntity> userEntries = chatEntryRepository.findByUserId(userId, pageable);
        
        // Filter the results based on criteria
        List<ChatEntryEntity> filteredContent = userEntries.getContent().stream()
                .filter(entry -> {
                    // Filter by author
                    if (author != null && !author.trim().isEmpty()) {
                        if (!entry.getAuthor().equalsIgnoreCase(author.trim())) {
                            return false;
                        }
                    }
                    
                    // Filter by type
                    if (type != null) {
                        if (!entry.getType().equals(type)) {
                            return false;
                        }
                    }
                    
                    // Filter by start date
                    if (startDate != null) {
                        if (entry.getLocalDateTime() == null || entry.getLocalDateTime().isBefore(startDate)) {
                            return false;
                        }
                    }
                    
                    // Filter by end date
                    if (endDate != null) {
                        if (entry.getLocalDateTime() == null || entry.getLocalDateTime().isAfter(endDate)) {
                            return false;
                        }
                    }
                    
                    // Filter by hasAttachment
                    if (hasAttachment != null) {
                        if (hasAttachment) {
                            if (entry.getAttachmentHash() == null || entry.getAttachmentHash().isEmpty()) {
                                return false;
                            }
                        } else {
                            if (entry.getAttachmentHash() != null && !entry.getAttachmentHash().isEmpty()) {
                                return false;
                            }
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
        
        // Create a new page with filtered content
        return new org.springframework.data.domain.PageImpl<>(
                filteredContent, 
                pageable, 
                userEntries.getTotalElements()
        );
    }
    
    /**
     * Search by keyword in payload and author (user-specific)
     */
    public Page<ChatEntryEntity> searchByKeyword(Long userId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.searchByUserIdAndKeyword(userId, keyword, pageable);
    }
    
    /**
     * Search by keyword in a specific chat (user-specific)
     */
    public Page<ChatEntryEntity> searchByKeywordInChat(Long userId, String chatId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.searchByUserIdAndChatIdAndKeyword(userId, chatId, keyword, pageable);
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
        Page<ChatEntryEntity> allEntries = chatEntryRepository.findAll(pageable);
        
        // Filter the results based on criteria
        List<ChatEntryEntity> filteredContent = allEntries.getContent().stream()
                .filter(entry -> {
                    // Filter by keyword
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        String searchKeyword = keyword.toLowerCase().trim();
                        String payload = entry.getPayload() != null ? entry.getPayload().toLowerCase() : "";
                        String entryAuthor = entry.getAuthor() != null ? entry.getAuthor().toLowerCase() : "";
                        
                        if (!payload.contains(searchKeyword) && !entryAuthor.contains(searchKeyword)) {
                            return false;
                        }
                    }
                    
                    // Filter by author
                    if (author != null && !author.trim().isEmpty()) {
                        if (!entry.getAuthor().equalsIgnoreCase(author.trim())) {
                            return false;
                        }
                    }
                    
                    // Filter by type
                    if (type != null) {
                        if (!entry.getType().equals(type)) {
                            return false;
                        }
                    }
                    
                    // Filter by start date
                    if (startDate != null) {
                        if (entry.getLocalDateTime() == null || entry.getLocalDateTime().isBefore(startDate)) {
                            return false;
                        }
                    }
                    
                    // Filter by end date
                    if (endDate != null) {
                        if (entry.getLocalDateTime() == null || entry.getLocalDateTime().isAfter(endDate)) {
                            return false;
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
        
        // Create a new page with filtered content
        return new org.springframework.data.domain.PageImpl<>(
                filteredContent, 
                pageable, 
                allEntries.getTotalElements()
        );
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
    public long countByAuthor(Long userId, String author) {
        return chatEntryRepository.countByUserIdAndAuthor(userId, author);
    }
    
    public long countByType(Long userId, ChatEntry.Type type) {
        return chatEntryRepository.countByUserIdAndType(userId, type);
    }
    
    public long countByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.countByUserIdAndLocalDateTimeBetween(userId, start, end);
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