package net.kem.whatsapp.chatviewer.shared.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ChatEntryEntity entity for the zipper service Maps to the chat_entries table in the
 * zipper-service database Must match the main service's ChatEntryEntity structure exactly
 */
@Entity
@Table(name = "chat_entries", indexes = {
        @Index(name = "idx_chat_entries_author", columnList = "author"),
        @Index(name = "idx_chat_entries_type", columnList = "type"),
        @Index(name = "idx_chat_entries_local_date_time", columnList = "local_date_time"),
        @Index(name = "idx_chat_entries_user_id", columnList = "user_id"),
        @Index(name = "idx_chat_entries_chat_id", columnList = "chat_id"),
        @Index(name = "idx_chat_entries_user_chat", columnList = "user_id, chat_id"),
        @Index(name = "idx_chat_entries_at_id", columnList = "at_id"),
        @Index(name = "idx_chat_entries_path", columnList = "path"),
        @Index(name = "idx_chat_entries_user_type", columnList = "user_id, type"),
        @Index(name = "idx_chat_entries_user_author", columnList = "user_id, author"),
        @Index(name = "idx_chat_entries_user_date", columnList = "user_id, local_date_time"),
        @Index(name = "idx_chat_entries_user_chat_date",
                columnList = "user_id, chat_id, local_date_time")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "author", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'Unknown'")
    private String author;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ChatEntry.Type type;

    @Column(name = "local_date_time")
    private LocalDateTime localDateTime;

    // User who owns this chat entry
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Chat ID this entry belongs to (based on uploaded filename)
    @Column(name = "chat_id", nullable = false)
    private String chatId;

    // Path to attachment file (hierarchical directory structure)
    @Column(name = "path")
    private String path;

    // Reference to attachment entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "at_id")
    private AttachmentEntity attachment;

    @PrePersist
    protected void onCreate() {
        if (author == null || author.trim().isEmpty()) {
            author = "Unknown";
        }
    }

    // Convert from shared ChatEntryEntity model to entity
    public static ChatEntryEntity fromSharedModel(
            net.kem.whatsapp.chatviewer.shared.model.ChatEntry sharedChatEntry, Long userId,
            String chatId) {
        return ChatEntryEntity.builder().payload(sharedChatEntry.getPayload())
                .author(sharedChatEntry.getAuthor()).fileName(sharedChatEntry.getFileName())
                .type(sharedChatEntry.getType()).localDateTime(sharedChatEntry.getLocalDateTime())
                .userId(userId).chatId(chatId).build();
    }

    // Convert entity back to shared ChatEntryEntity model
    public ChatEntry toSharedModel() {
        return ChatEntry.builder().payload(payload).author(author).fileName(fileName)
                .type(type != null ? type : ChatEntry.Type.UNKNOWN).localDateTime(localDateTime)
                .userId(userId).chatId(chatId)
                .attachmentHash(attachment != null ? attachment.getHash() : null).build();
    }

    // Convert from ChatEntry model to entity
    public static ChatEntryEntity fromChatEntry(ChatEntry chatEntry, Long userId, String chatId) {
        return ChatEntryEntity.builder().payload(chatEntry.getPayload())
                .author(chatEntry.getAuthor()).fileName(chatEntry.getFileName())
                .type(chatEntry.getType()).localDateTime(chatEntry.getLocalDateTime())
                // .attachment(chatEntry.getAttachmentHash()).userId(userId).chatId(chatId)
                .build();
    }

    /**
     * Check if this entry has an attachment
     */
    public boolean hasAttachment() {
        return attachment != null;
    }

    /**
     * Check if this is a text message
     */
    public boolean isTextMessage() {
        return ChatEntry.Type.TEXT.equals(type);
    }

    /**
     * Check if this is a multimedia message
     */
    public boolean isMultimediaMessage() {
        return type != null && !ChatEntry.Type.TEXT.equals(type);
    }
}
