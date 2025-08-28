package net.kem.whatsapp.chatviewer.zipper.service;

import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;
import net.kem.whatsapp.chatviewer.shared.model.AttachmentEntity;

import java.util.List;
import java.util.Map;

/**
 * Service for managing chat data operations in the local zipper-service database
 */
public interface ChatDataService {

    /**
     * Save chat entries to the local database
     */
    void pendChatEntries(Long userId, String chatId, List<ChatEntry> chatEntries,
                         Map<String, String> filenameToChecksum);

    void pendAttachments(Long userId, String chatId, List<AttachmentEntity> attachments);

    /**
     * Commit all pending changes to the database
     */
    void commitPendingChanges();

    /**
     * Check if a chat entry already exists
     */
    boolean chatEntryExists(Long userId, String timestamp, String author, String payload);

    /**
     * Get chat entries for a specific user and chat
     */
    List<ChatEntry> getChatEntries(Long userId, String chatId);

    /**
     * Get chat entries for a specific user with pagination
     */
    org.springframework.data.domain.Page<ChatEntry> getChatEntriesByUserId(Long userId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Count chat entries for a specific user
     */
    long countChatEntriesByUserId(Long userId);

    /**
     * Count chat entries for a specific user and chat
     */
    long countChatEntriesByUserIdAndChatId(Long userId, String chatId);
}
