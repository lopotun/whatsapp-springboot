package net.kem.whatsapp.chatviewer.zipper.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;
import net.kem.whatsapp.chatviewer.shared.model.AttachmentEntity;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.shared.repository.AttachmentRepository;
import net.kem.whatsapp.chatviewer.zipper.repository.ChatEntryRepository;
import net.kem.whatsapp.chatviewer.zipper.service.ChatDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ChatDataService for managing chat data in the local zipper-service database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatDataServiceImpl implements ChatDataService {

    private final ChatEntryRepository chatEntryRepository;
    private final AttachmentRepository attachmentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private PendingChatEntryOperation pendingChatEntryOperation;
    private PendingAttachmentOperation pendingAttachmentOperation;

    private record PendingChatEntryOperation(Long userId, String chatId,
                                             List<ChatEntry> chatEntries, Map<String, String> filenameToChecksum) {
    }

    private record PendingAttachmentOperation(Long userId, String chatId, List<AttachmentEntity> attachments) {
    }

    @Override
    public void pendChatEntries(Long userId, String chatId, List<ChatEntry> chatEntries,
                                Map<String, String> filenameToChecksum) {
        log.info("Queueing {} chat entries for user {} and chat {} to be committed later",
                chatEntries.size(), userId, chatId);

        // Store operation for later processing
        pendingChatEntryOperation = new PendingChatEntryOperation(userId, chatId, chatEntries, filenameToChecksum);
    }

    @Override
    public void pendAttachments(Long userId, String chatId, List<AttachmentEntity> attachments) {
        log.info("Queueing {} attachments for user {} and chat {} to be committed later",
                attachments.size(), userId, chatId);

        // Store operation for later processing
        pendingAttachmentOperation = new PendingAttachmentOperation(userId, chatId, attachments);
    }

    @Override
    public void commitPendingChanges() {
        log.info("Committing all pending changes to database");
        if (pendingChatEntryOperation == null) {
            log.info("No pending operations to commit");
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            processAttachmentOperation(pendingAttachmentOperation);
            processChatEntryOperation(pendingChatEntryOperation);
        });

        log.info("Successfully committed all pending changes to database");
    }

    private void processAttachmentOperation(PendingAttachmentOperation pendingAttachmentOperation) {
        List<String> hashes = pendingAttachmentOperation.attachments.stream().map(AttachmentEntity::getHash).toList();
        Set<String> existingHashes = attachmentRepository.findByHashIn(hashes).stream()
            .map(AttachmentEntity::getHash)
            .collect(Collectors.toSet());
        pendingAttachmentOperation.attachments.removeIf(att -> existingHashes.contains(att.getHash()));
        attachmentRepository.saveAll(pendingAttachmentOperation.attachments);
    }

    private void processChatEntryOperation(PendingChatEntryOperation operation) {
        try {
            // Remove duplicates and save to database
            List<ChatEntry> uniqueEntries = deduplicateEntries(operation.chatEntries);

            Page<ChatEntryEntity> previousChat =
                chatEntryRepository.findByUserIdAndChatId(operation.userId, operation.chatId, Pageable.unpaged());

            if(!previousChat.isEmpty()) {
                log.info("Found {} previous chat entries for user {} and chat {}",
                    previousChat.getTotalElements(), operation.userId, operation.chatId);

                Map<String, ChatEntryEntity> collected = previousChat.stream()
                        .collect(Collectors.toMap(this::createEntryKey, ce -> ce));

                // Remove duplicates from the new entries based on existing ones
                uniqueEntries.removeIf(ce -> {
                    String key = createEntryKey(ce);
                    boolean res = collected.containsKey(key);
                    collected.remove(key); // to keep track of what we have seen
                    return res;
                });

                if(!collected.isEmpty()) {
                    // These existing entries were not found in the new upload, remove them
                    log.info("Removing {} chat entries from local database for user {} and chat {} that were not found in the new upload",
                        collected.size(), operation.userId, operation.chatId);
                    chatEntryRepository.deleteAll(collected.values());
                }
                log.info("After removing duplicates, {} new chat entries remain for user {} and chat {}",
                    uniqueEntries.size(), operation.userId, operation.chatId);
            } else {
                log.info("No previous chat entries found for user {} and chat {}", operation.userId, operation.chatId);
            }

            // Convert (remained) shared ChatEntryEntity models to local entities
            List<ChatEntryEntity> entities = uniqueEntries.stream()
                    .map(sharedEntry -> ChatEntryEntity.fromSharedModel(
                        sharedEntry, operation.userId, operation.chatId))
                    .collect(Collectors.toList());

            // Link attachments to chat entries based on filename mapping

            // Save all chat entries
            List<ChatEntryEntity> savedEntries = chatEntryRepository.saveAll(entities);
            linkAttachmentsToEntries(savedEntries, operation.filenameToChecksum);

            log.info("Successfully saved {} chat entries to local database for user {} and chat {}",
                    savedEntries.size(), operation.userId, operation.chatId);

        } catch (Exception e) {
            log.error("Error saving chat entries to local database for user {} and chat {}: {}",
                    operation.userId, operation.chatId, e.getMessage(), e);
            throw new RuntimeException("Failed to save chat entries to local database", e);
        }
    }

    // The commitAllChanges method is now replaced by the new commitPendingChanges method
    // This method is kept for backward compatibility if needed
    public void commitAllChanges(List<ChatEntry> entries, Long userId, String chatId, Map<String, String> filenameToChecksum) {
        pendChatEntries(userId, chatId, entries, filenameToChecksum);
        commitPendingChanges();
    }

    @Override
    public boolean chatEntryExists(Long userId, String timestamp, String author, String payload) {
        log.debug("Checking if chat entry exists for user {}: {} - {} - {}", userId, timestamp,
                author, payload);

        try {
            // Parse timestamp to LocalDateTime
            LocalDateTime localDateTime = LocalDateTime.parse(timestamp);

            // Check if entry exists in local database
            return chatEntryRepository.existsByUniqueFields(userId, localDateTime, author, payload);

        } catch (Exception e) {
            log.error("Failed to check if chat entry exists for user {}: {} - {} - {}: {}", userId,
                    timestamp, author, payload, e.getMessage(), e);
            // If we can't check, assume it doesn't exist to avoid blocking the upload
            return false;
        }
    }

    @Override
    public List<net.kem.whatsapp.chatviewer.shared.model.ChatEntry> getChatEntries(Long userId, String chatId) {
        log.debug("Getting chat entries for user {} and chat {}", userId, chatId);

        List<ChatEntryEntity> entities = chatEntryRepository.findByUserIdAndChatIdOrderByLocalDateTimeAsc(userId, chatId);
        return entities.stream()
                .map(ChatEntryEntity::toSharedModel)
                .collect(Collectors.toList());
    }

    @Override
    public Page<net.kem.whatsapp.chatviewer.shared.model.ChatEntry> getChatEntriesByUserId(Long userId, Pageable pageable) {
        log.debug("Getting chat entries for user {} with pagination", userId);

        Page<ChatEntryEntity> entities = chatEntryRepository.findByUserId(userId, pageable);
        return entities.map(ChatEntryEntity::toSharedModel);
    }

    @Override
    public long countChatEntriesByUserId(Long userId) {
        return chatEntryRepository.countByUserId(userId);
    }

    @Override
    public long countChatEntriesByUserIdAndChatId(Long userId, String chatId) {
        return chatEntryRepository.countByUserIdAndChatId(userId, chatId);
    }

    /**
     * Link attachments to chat entries based on filename mapping
     */
    private void linkAttachmentsToEntries(List<ChatEntryEntity> entries,
            Map<String, String> filenameToChecksum) {
        List<ChatEntryEntity> entriesToSave = new ArrayList<>();

        for (ChatEntryEntity entry : entries) {
            if (entry.getFileName() != null
                    && filenameToChecksum.containsKey(entry.getFileName())) {
                String hash = filenameToChecksum.get(entry.getFileName());

                // Find the attachment by hash
                attachmentRepository.findByHash(hash).ifPresent(attachment -> {
                    entry.setAttachment(attachment);
                    entry.setPath(generateFilePath(hash));

                    log.debug("Linked attachment {} to chat entry {} with filename {}", hash,
                            entry.getId(), entry.getFileName());
                });
                entriesToSave.add(entry);
            }
        }

        // Save all entries with their attachments at once
        if (!entriesToSave.isEmpty()) {
            chatEntryRepository.saveAll(entriesToSave);
            log.info("Saved {} chat entries with attachment links", entriesToSave.size());
        }
    }

    /**
     * Generate file path for attachment based on hash
     */
    private String generateFilePath(String hash) {
        if (hash == null || hash.length() < 6) {
            return null;
        }
        return hash.substring(0, 3) + "/" + hash.substring(3, 6) + "/" + hash;
    }

    /**
     * Remove duplicate entries based on content
     */
    private List<ChatEntry> deduplicateEntries(List<ChatEntry> entries) {
        Map<String, ChatEntry> uniqueEntries = new HashMap<>();
        for (ChatEntry entry : entries) {
            ee(entry, uniqueEntries);
        }
        return new ArrayList<>(uniqueEntries.values());
    }

    private void ee(ChatEntry entry, Map<String, ChatEntry> uniqueEntries) {
        String key = createEntryKey(entry);
        ChatEntry existingChatEntry = uniqueEntries.get(key);
        if (existingChatEntry == null) {
            uniqueEntries.put(key, entry);
        } else {
            if(!existingChatEntry.getPayload().equals(entry.getPayload())) {
                LocalDateTime plusSeconds = existingChatEntry.getLocalDateTime().plusSeconds(1);// make a small adjustment to avoid exact duplicate
                entry.setLocalDateTime(plusSeconds);
                ee(entry, uniqueEntries);
            }
            log.debug("Duplicate entry {} will be skipped", key);
        }
    }

    /**
     * Create a unique key for a chat entry based on its content. This is used to identify duplicate
     * entries across uploads
     */
    private String createEntryKey(ChatEntry entry) {
        return String.format("%s_%s_%s", entry.getLocalDateTime(), entry.getAuthor(),
            entry.getFileName() != null ? entry.getFileName() : "");
    }

    private String createEntryKey(ChatEntryEntity entry) {
        return String.format("%s_%s_%s", entry.getLocalDateTime(), entry.getAuthor(),
            entry.getFileName() != null ? entry.getFileName() : "");
    }
}
