package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
import lombok.extern.slf4j.Slf4j;

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
    public List<ChatEntryEntity> saveChatEntries(List<ChatEntry> chatEntries, Long userId,
            String chatId) {
        List<ChatEntryEntity> entities = chatEntries.stream()
                .map(chatEntry -> ChatEntryEntity.fromChatEntry(chatEntry, userId, chatId))
                .collect(Collectors.toList());

        List<ChatEntryEntity> saved = chatEntryRepository.saveAll(entities);
        log.info("Saved {} chat entries in batch for user: {} and chat: {}", saved.size(), userId,
                chatId);
        return saved;
    }

    /**
     * Find chat entry by ID (user-specific)
     */
    public Optional<ChatEntryEntity> findById(Long id, Long userId) {
        return chatEntryRepository.findById(id).filter(entry -> entry.getUserId().equals(userId));
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
    public Page<ChatEntryEntity> findByUserIdAndChatId(Long userId, String chatId, int page,
            int size) {
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
    public Page<ChatEntryEntity> searchChatEntries(Long userId, String author, ChatEntry.Type type,
            LocalDateTime startDate, LocalDateTime endDate, Boolean hasAttachment,
            List<String> chatIds, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatEntryEntity> results;

        // Use optimized database queries based on the criteria provided
        if (type != null) {
            // If type is specified, use the optimized type-based query
            if (chatIds != null && !chatIds.isEmpty()) {
                // Type + chat filter - we need to implement this or use the general approach
                results = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
            } else {
                // Type only - use optimized query
                results = chatEntryRepository.findByUserIdAndType(userId, type, pageable);
            }
        } else if (author != null && !author.trim().isEmpty()) {
            // If author is specified, use the optimized author-based query
            if (chatIds != null && !chatIds.isEmpty()) {
                // Author + chat filter - we need to implement this or use the general approach
                results = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
            } else {
                // Author only - use optimized query
                results =
                        chatEntryRepository.findByUserIdAndAuthor(userId, author.trim(), pageable);
            }
        } else if (startDate != null || endDate != null) {
            // If date range is specified, use the optimized date-based query
            LocalDateTime start = startDate != null ? startDate : LocalDateTime.MIN;
            LocalDateTime end = endDate != null ? endDate : LocalDateTime.MAX;

            if (chatIds != null && !chatIds.isEmpty()) {
                // Date + chat filter - we need to implement this or use the general approach
                results = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
            } else {
                // Date only - use optimized query
                results = chatEntryRepository.findByUserIdAndLocalDateTimeBetween(userId, start,
                        end, pageable);
            }
        } else {
            // General case - use the basic user query
            if (chatIds != null && !chatIds.isEmpty()) {
                results = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
            } else {
                results = chatEntryRepository.findByUserId(userId, pageable);
            }
        }

        // Apply additional filters in memory for complex combinations
        List<ChatEntryEntity> filteredContent = results.getContent().stream().filter(entry -> {
            // Filter by author (if not already filtered by database)
            if (author != null && !author.trim().isEmpty() && type == null) {
                if (!entry.getAuthor().equalsIgnoreCase(author.trim())) {
                    return false;
                }
            }

            // Filter by type (if not already filtered by database)
            if (type != null && author == null && startDate == null && endDate == null) {
                if (!entry.getType().equals(type)) {
                    return false;
                }
            }

            // Filter by start date (if not already filtered by database)
            if (startDate != null && type == null && author == null) {
                if (entry.getLocalDateTime() == null
                        || entry.getLocalDateTime().isBefore(startDate)) {
                    return false;
                }
            }

            // Filter by end date (if not already filtered by database)
            if (endDate != null && type == null && author == null) {
                if (entry.getLocalDateTime() == null || entry.getLocalDateTime().isAfter(endDate)) {
                    return false;
                }
            }

            // Filter by hasAttachment (always in memory as it's complex)
            if (hasAttachment != null) {
                if (hasAttachment) {
                    if (entry.getFileName() == null || entry.getFileName().isEmpty()) {
                        return false;
                    }
                } else {
                    if (entry.getFileName() != null && !entry.getFileName().isEmpty()) {
                        return false;
                    }
                }
            }

            return true;
        }).collect(Collectors.toList());

        // Create a new page with filtered content and sanitize results
        Page<ChatEntryEntity> filteredPage = new org.springframework.data.domain.PageImpl<>(
                filteredContent, pageable, results.getTotalElements());
        return sanitizeResults(filteredPage);
    }

    /**
     * Search by keyword in payload and author (user-specific)
     */
    public Page<ChatEntryEntity> searchByKeyword(Long userId, String keyword, List<String> chatIds,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        log.info("Searching by keyword: '{}' for user: {} in chats: {} (page: {}, size: {})",
                keyword, userId, chatIds, page, size);

        // If specific chat IDs are provided, search in those chats
        if (chatIds != null && !chatIds.isEmpty()) {
            Page<ChatEntryEntity> results = chatEntryRepository
                    .searchByUserIdAndChatIdInAndKeyword(userId, chatIds, keyword, pageable);
            log.info("Keyword search with chat filter returned {} results",
                    results.getTotalElements());
            return sanitizeResults(results);
        } else {
            Page<ChatEntryEntity> results =
                    chatEntryRepository.searchByUserIdAndKeyword(userId, keyword, pageable);
            log.info("Keyword search without chat filter returned {} results",
                    results.getTotalElements());
            return sanitizeResults(results);
        }
    }

    /**
     * Search by keyword in a specific chat (user-specific)
     */
    public Page<ChatEntryEntity> searchByKeywordInChat(Long userId, String chatId, String keyword,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatEntryEntity> results = chatEntryRepository
                .searchByUserIdAndChatIdAndKeyword(userId, chatId, keyword, pageable);
        return sanitizeResults(results);
    }

    /**
     * Advanced search with keyword and other criteria
     */
    public Page<ChatEntryEntity> advancedSearch(Long userId, String keyword, String author,
            ChatEntry.Type type, LocalDateTime startDate, LocalDateTime endDate,
            List<String> chatIds, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatEntryEntity> allEntries;

        // If specific chat IDs are provided, filter by them
        if (chatIds != null && !chatIds.isEmpty()) {
            allEntries = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
        } else {
            allEntries = chatEntryRepository.findByUserId(userId, pageable);
        }

        // Filter the results based on criteria
        List<ChatEntryEntity> filteredContent = allEntries.getContent().stream().filter(entry -> {
            // Filter by keyword
            if (keyword != null && !keyword.trim().isEmpty()) {
                String searchKeyword = keyword.toLowerCase().trim();
                String payload = entry.getPayload() != null ? entry.getPayload().toLowerCase() : "";
                String entryAuthor =
                        entry.getAuthor() != null ? entry.getAuthor().toLowerCase() : "";

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
                if (entry.getLocalDateTime() == null
                        || entry.getLocalDateTime().isBefore(startDate)) {
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
        }).collect(Collectors.toList());

        // Create a new page with filtered content and sanitize results
        Page<ChatEntryEntity> filteredPage = new org.springframework.data.domain.PageImpl<>(
                filteredContent, pageable, allEntries.getTotalElements());
        return sanitizeResults(filteredPage);
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
    public List<ChatEntryEntity> findByAuthorAndDateRange(String author, LocalDateTime start,
            LocalDateTime end) {
        return chatEntryRepository.findByAuthorAndLocalDateTimeBetween(author, start, end);
    }

    /**
     * Find entries by type and date range
     */
    public List<ChatEntryEntity> findByTypeAndDateRange(ChatEntry.Type type, LocalDateTime start,
            LocalDateTime end) {
        return chatEntryRepository.findByTypeAndLocalDateTimeBetween(type, start, end);
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

    /**
     * Sanitize search results to remove sensitive data
     */
    private Page<ChatEntryEntity> sanitizeResults(Page<ChatEntryEntity> results) {
        List<ChatEntryEntity> sanitizedContent =
                results.getContent().stream().map(this::sanitizeEntry).collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(sanitizedContent,
                results.getPageable(), results.getTotalElements());
    }

    /**
     * Sanitize individual chat entry to remove sensitive data
     */
    private ChatEntryEntity sanitizeEntry(ChatEntryEntity entry) {
        if (entry.getPayload() != null) {
            String sanitizedPayload = sanitizePayload(entry.getPayload());
            entry.setPayload(sanitizedPayload);
        }
        return entry;
    }

    /**
     * Remove sensitive data patterns from payload
     */
    private String sanitizePayload(String payload) {
        if (payload == null) {
            return null;
        }

        // Remove join-key and master-key patterns
        String sanitized = payload.replaceAll("(?i)join-key:\\s*[a-f0-9]+", "join-key: [REDACTED]")
                .replaceAll("(?i)master-key:\\s*[a-f0-9]+", "master-key: [REDACTED]")
                .replaceAll("(?i)api-key:\\s*[a-f0-9]+", "api-key: [REDACTED]")
                .replaceAll("(?i)secret:\\s*[a-f0-9]+", "secret: [REDACTED]")
                .replaceAll("(?i)password:\\s*[^\\s]+", "password: [REDACTED]")
                .replaceAll("(?i)token:\\s*[a-f0-9]+", "token: [REDACTED]");

        return sanitized;
    }
}
