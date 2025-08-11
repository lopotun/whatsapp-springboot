package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class ChatEntryService {

    private final ChatEntryRepository chatEntryRepository;
    private final FileNamingService fileNamingService;

    @Autowired
    public ChatEntryService(ChatEntryRepository chatEntryRepository,
            FileNamingService fileNamingService) {
        this.chatEntryRepository = chatEntryRepository;
        this.fileNamingService = fileNamingService;
    }

    /**
     * Save a single chat entry entity directly
     */
    public ChatEntryEntity saveChatEntry(ChatEntryEntity entity) {
        ChatEntryEntity saved = chatEntryRepository.save(entity);
        log.debug("Saved chat entry: {} for user: {} and chat: {}", saved.getId(),
                saved.getUserId(), saved.getChatId());
        return saved;
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
     * Save multiple chat entries in batch with user and chat association
     */
    public List<ChatEntryEntity> saveChatEntries(List<ChatEntryEntity> chatEntryEntities) {
        if (CollectionUtils.isEmpty(chatEntryEntities)) {
            log.warn("No chat entries provided for batch save");
            return List.of();
        }
        List<ChatEntryEntity> saved = chatEntryRepository.saveAll(chatEntryEntities);
        log.info("Saved {} chat entries in batch for user: {} and chat: {}", saved.size(),
                chatEntryEntities.getFirst().getUserId(), chatEntryEntities.getFirst().getChatId());
        return saved;
    }

    /**
     * Check if a chat entry already exists based on unique constraint fields
     */
    public boolean existsByUniqueFields(Long userId, String chatId, LocalDateTime localDateTime,
            String author, String payload, String fileName) {
        try {
            return chatEntryRepository.existsByUniqueFields(userId, chatId, localDateTime, author,
                    payload, fileName);
        } catch (Exception e) {
            log.warn("Error checking for existing entry: user={}, chat={}, time={}, author={} - {}",
                    userId, chatId, localDateTime, author, e.getMessage());
            // If we can't check, assume it doesn't exist to avoid blocking the upload
            return false;
        }
    }

    /**
     * Find chat entry by ID (user-specific)
     */
    @Transactional(readOnly = true)
    public Optional<ChatEntryEntity> findById(Long id, Long userId) {
        return chatEntryRepository.findById(id).filter(entry -> entry.getUserId().equals(userId));
    }

    /**
     * Get all chat entries for a user
     */
    @Transactional(readOnly = true)
    public Page<ChatEntryEntity> findByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.findByUserId(userId, pageable);
    }

    /**
     * Get all chat entries for a specific chat of a user
     */
    @Transactional(readOnly = true)
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
    public void deleteByUserIdAndChatId(Long userId, String chatId) {
        chatEntryRepository.deleteByUserIdAndChatId(userId, chatId);
        log.info("Deleted all chat entries for user: {} and chat: {}", userId, chatId);
    }

    /**
     * Delete a specific chat entry (user-specific)
     */
    public void deleteById(Long id, Long userId) {
        Optional<ChatEntryEntity> entry = findById(id, userId);
        if (entry.isPresent()) {
            chatEntryRepository.deleteById(id);
            log.info("Deleted chat entry: {} for user: {}", id, userId);
        } else {
            log.warn("Attempted to delete non-existent chat entry: {} for user: {}", id, userId);
        }
    }

    /**
     * Search chat entries with multiple criteria (user-specific)
     */
    @Transactional(readOnly = true)
    public Page<ChatEntryEntity> searchChatEntries(Long userId, String author, ChatEntry.Type type,
            LocalDateTime startDate, LocalDateTime endDate, Boolean hasAttachment,
            List<String> chatIds, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            // Use database-level filtering for better performance and to search all pages
            Page<ChatEntryEntity> results;

            if (type != null) {
                // If type is specified, use the specific repository method
                if (chatIds != null && !chatIds.isEmpty()) {
                    results = chatEntryRepository.findByUserIdAndChatIdInAndType(userId, chatIds,
                            type, pageable);
                } else {
                    results = chatEntryRepository.findByUserIdAndType(userId, type, pageable);
                }
            } else if (author != null && !author.trim().isEmpty()) {
                // If author is specified, use the specific repository method
                if (chatIds != null && !chatIds.isEmpty()) {
                    results = chatEntryRepository.findByUserIdAndChatIdInAndAuthor(userId, chatIds,
                            author.trim(), pageable);
                } else {
                    results = chatEntryRepository.findByUserIdAndAuthor(userId, author.trim(),
                            pageable);
                }
            } else if (startDate != null || endDate != null) {
                // If date range is specified, use the specific repository method
                LocalDateTime start = startDate != null ? startDate : LocalDateTime.MIN;
                LocalDateTime end = endDate != null ? endDate : LocalDateTime.MAX;
                if (chatIds != null && !chatIds.isEmpty()) {
                    results = chatEntryRepository.findByUserIdAndChatIdInAndLocalDateTimeBetween(
                            userId, chatIds, start, end, pageable);
                } else {
                    results = chatEntryRepository.findByUserIdAndLocalDateTimeBetween(userId, start,
                            end, pageable);
                }
            } else if (chatIds != null && !chatIds.isEmpty()) {
                // If only chatIds are specified
                results = chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
            } else {
                // Default case - get all entries for user
                results = chatEntryRepository.findByUserId(userId, pageable);
            }

            // Apply additional filters in memory only for complex combinations
            // that can't be handled by database queries
            List<ChatEntryEntity> filteredContent = results.getContent().stream().filter(entry -> {
                // Filter by hasAttachment (this can't be easily done at database level)
                if (hasAttachment != null) {
                    if (hasAttachment) {
                        if (entry.getPath() == null || entry.getPath().isEmpty()) {
                            return false;
                        }
                    } else {
                        if (entry.getPath() != null && !entry.getPath().isEmpty()) {
                            return false;
                        }
                    }
                }

                return true;
            }).collect(Collectors.toList());

            // Create a new page with filtered content
            Page<ChatEntryEntity> filteredPage = new org.springframework.data.domain.PageImpl<>(
                    filteredContent, pageable, results.getTotalElements());

            return sanitizeResults(filteredPage);
        } catch (Exception e) {
            log.error("Error during chat entry search for user: {} - {}", userId, e.getMessage(),
                    e);
            // Return empty results instead of throwing exception
            Pageable pageable = PageRequest.of(page, size);
            Page<ChatEntryEntity> emptyPage =
                    new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
            log.debug("Returning empty page for failed search: {}", emptyPage);
            return emptyPage;
        }
    }

    /**
     * Search chat entries by keyword (user-specific)
     */
    @Transactional(readOnly = true)
    public Page<ChatEntryEntity> searchByKeyword(Long userId, String keyword, List<String> chatIds,
            int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Page<ChatEntryEntity> results;

            if (chatIds != null && !chatIds.isEmpty()) {
                results = chatEntryRepository.searchByUserIdAndChatIdInAndKeyword(userId, chatIds,
                        keyword, pageable);
            } else {
                results = chatEntryRepository.searchByUserIdAndKeyword(userId, keyword, pageable);
            }

            return sanitizeResults(results);
        } catch (Exception e) {
            log.error("Error during keyword search for user: {} - {}", userId, e.getMessage(), e);
            // Return empty results instead of throwing exception
            Pageable pageable = PageRequest.of(page, size);
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Search chat entries by keyword in a specific chat (user-specific)
     */
    @Transactional(readOnly = true)
    public Page<ChatEntryEntity> searchByKeywordInChat(Long userId, String chatId, String keyword,
            int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Page<ChatEntryEntity> results = chatEntryRepository
                    .searchByUserIdAndChatIdAndKeyword(userId, chatId, keyword, pageable);

            return sanitizeResults(results);
        } catch (Exception e) {
            log.error(
                    "Error during keyword search in chat for user: {}, chat: {}, keyword: {} - {}",
                    userId, chatId, keyword, e.getMessage(), e);
            // Return empty results instead of throwing exception
            Pageable pageable = PageRequest.of(page, size);
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Advanced search with multiple criteria (user-specific)
     */
    @Transactional(readOnly = true)
    public Page<ChatEntryEntity> advancedSearch(Long userId, String keyword, String author,
            ChatEntry.Type type, LocalDateTime startDate, LocalDateTime endDate,
            List<String> chatIds, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            // Start with keyword search if provided
            Page<ChatEntryEntity> results;
            if (keyword != null && !keyword.trim().isEmpty()) {
                if (chatIds != null && !chatIds.isEmpty()) {
                    results = chatEntryRepository.searchByUserIdAndChatIdInAndKeyword(userId,
                            chatIds, keyword, pageable);
                } else {
                    results =
                            chatEntryRepository.searchByUserIdAndKeyword(userId, keyword, pageable);
                }
            } else {
                // No keyword, use basic user query
                if (chatIds != null && !chatIds.isEmpty()) {
                    results =
                            chatEntryRepository.findByUserIdAndChatIdIn(userId, chatIds, pageable);
                } else {
                    results = chatEntryRepository.findByUserId(userId, pageable);
                }
            }

            // Apply additional filters in memory
            List<ChatEntryEntity> filteredContent = results.getContent().stream().filter(entry -> {
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
                    if (entry.getLocalDateTime() == null
                            || entry.getLocalDateTime().isAfter(endDate)) {
                        return false;
                    }
                }

                return true;
            }).collect(Collectors.toList());

            // Create a new page with filtered content
            Page<ChatEntryEntity> filteredPage = new org.springframework.data.domain.PageImpl<>(
                    filteredContent, pageable, results.getTotalElements());

            return sanitizeResults(filteredPage);
        } catch (Exception e) {
            log.error("Error during advanced search for user: {} - {}", userId, e.getMessage(), e);
            // Return empty results instead of throwing exception
            Pageable pageable = PageRequest.of(page, size);
            return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Find chat entries by author (user-specific)
     */
    public List<ChatEntryEntity> findByAuthor(Long userId, String author) {
        return chatEntryRepository.findByUserIdAndAuthor(userId, author);
    }

    /**
     * Find chat entries by type (user-specific)
     */
    public List<ChatEntryEntity> findByType(Long userId, ChatEntry.Type type) {
        return chatEntryRepository.findByUserIdAndType(userId, type);
    }

    /**
     * Find chat entries by date range (user-specific)
     */
    public List<ChatEntryEntity> findByDateRange(Long userId, LocalDateTime start,
            LocalDateTime end) {
        return chatEntryRepository.findByUserIdAndLocalDateTimeBetween(userId, start, end);
    }

    /**
     * Find chat entries by author and type (user-specific)
     */
    public List<ChatEntryEntity> findByAuthorAndType(Long userId, String author,
            ChatEntry.Type type) {
        return chatEntryRepository.findByUserIdAndAuthorAndType(userId, author, type);
    }

    /**
     * Find chat entries by author and date range (user-specific)
     */
    public List<ChatEntryEntity> findByAuthorAndDateRange(Long userId, String author,
            LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.findByUserIdAndAuthorAndLocalDateTimeBetween(userId, author,
                start, end);
    }

    /**
     * Find chat entries by type and date range (user-specific)
     */
    public List<ChatEntryEntity> findByTypeAndDateRange(Long userId, ChatEntry.Type type,
            LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.findByUserIdAndTypeAndLocalDateTimeBetween(userId, type, start,
                end);
    }

    /**
     * Count chat entries by author (user-specific)
     */
    public long countByAuthor(Long userId, String author) {
        return chatEntryRepository.countByUserIdAndAuthor(userId, author);
    }

    /**
     * Count chat entries by type (user-specific)
     */
    public long countByType(Long userId, ChatEntry.Type type) {
        return chatEntryRepository.countByUserIdAndType(userId, type);
    }

    /**
     * Count chat entries by date range (user-specific)
     */
    public long countByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return chatEntryRepository.countByUserIdAndLocalDateTimeBetween(userId, start, end);
    }

    /**
     * Delete a chat entry by ID
     */
    public void deleteById(Long id) {
        chatEntryRepository.deleteById(id);
    }

    /**
     * Get all chat entries with pagination
     */
    public Page<ChatEntryEntity> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return chatEntryRepository.findAll(pageable);
    }

    /**
     * Sanitize search results to remove sensitive information Creates defensive copies to avoid
     * modifying the original entities
     */
    private Page<ChatEntryEntity> sanitizeResults(Page<ChatEntryEntity> results) {
        if (results == null || results.getContent() == null) {
            log.warn("Attempted to sanitize null or empty results");
            return results;
        }

        // Create defensive copies to avoid modifying original entities
        List<ChatEntryEntity> sanitizedContent = results.getContent().stream()
                .map(this::createSanitizedCopy).collect(Collectors.toList());

        // Create a new page with sanitized content
        return new org.springframework.data.domain.PageImpl<>(sanitizedContent,
                results.getPageable(), results.getTotalElements());
    }

    /**
     * Create a sanitized copy of a chat entry to avoid modifying the original
     */
    private ChatEntryEntity createSanitizedCopy(ChatEntryEntity entry) {
        if (entry == null) {
            log.warn("Attempted to sanitize null entry");
            return null;
        }

        // Create a new entity with the same data
        ChatEntryEntity copy = new ChatEntryEntity();
        copy.setId(entry.getId());
        copy.setUserId(entry.getUserId());
        copy.setChatId(entry.getChatId());
        copy.setAuthor(entry.getAuthor());
        copy.setLocalDateTime(entry.getLocalDateTime());
        copy.setType(entry.getType());
        copy.setPath(entry.getPath());
        copy.setFileName(entry.getFileName());
        copy.setAttachment(entry.getAttachment());

        // Sanitize the payload if it exists
        if (entry.getPayload() != null) {
            copy.setPayload(sanitizePayload(entry.getPayload()));
        } else {
            copy.setPayload(null);
        }

        return copy;
    }

    /**
     * Sanitize a single chat entry to remove sensitive information
     *
     * @deprecated Use createSanitizedCopy instead to avoid modifying original entities
     */
    @Deprecated
    private ChatEntryEntity sanitizeEntry(ChatEntryEntity entry) {
        if (entry == null) {
            log.warn("Attempted to sanitize null entry");
            return null;
        }
        if (entry.getPayload() != null) {
            entry.setPayload(sanitizePayload(entry.getPayload()));
        }
        return entry;
    }

    /**
     * Sanitize payload content to remove sensitive information
     */
    private String sanitizePayload(String payload) {
        if (payload == null) {
            return null;
        }

        String sanitized = payload;
        // Remove phone numbers (basic pattern)
        sanitized = sanitized.replaceAll("\\b\\d{10,15}\\b", "[PHONE_NUMBER]");
        // Remove email addresses
        sanitized = sanitized.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
                "[EMAIL]");
        // Remove URLs
        sanitized = sanitized.replaceAll("https?://[^\\s]+", "[URL]");

        return sanitized;
    }

    /**
     * Download attachment for a chat entry (user-specific)
     */
    public Resource downloadAttachment(Long chatEntryId, Long userId) throws IOException {
        // Find the chat entry and verify user ownership
        Optional<ChatEntryEntity> chatEntryOpt = findById(chatEntryId, userId);

        if (chatEntryOpt.isEmpty()) {
            throw new IOException("Chat entry not found or access denied: " + chatEntryId);
        }

        ChatEntryEntity chatEntry = chatEntryOpt.get();

        // Check if the chat entry has an attachment
        if (chatEntry.getPath() == null || chatEntry.getPath().isEmpty()) {
            throw new IOException("No attachment found for chat entry: " + chatEntryId);
        }

        // Generate the file path using the configured multimedia storage path
        Path foundFile = fileNamingService.generateFilePathFromPath(chatEntry.getPath());

        if (foundFile == null) {
            throw new IOException("Could not generate file path for: " + chatEntry.getPath());
        }

        // Check if the file actually exists
        if (!Files.exists(foundFile)) {
            throw new IOException("File not found at path: " + foundFile);
        }

        return new FileSystemResource(foundFile.toFile());
    }
}
