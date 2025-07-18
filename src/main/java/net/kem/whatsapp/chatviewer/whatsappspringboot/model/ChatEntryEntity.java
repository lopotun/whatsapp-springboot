package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_entries", indexes = {
    @Index(name = "idx_chat_entries_author", columnList = "author"),
    @Index(name = "idx_chat_entries_type", columnList = "type"),
    @Index(name = "idx_chat_entries_local_date_time", columnList = "local_date_time"),
    @Index(name = "idx_chat_entries_author_type", columnList = "author, type"),
    @Index(name = "idx_chat_entries_date_author", columnList = "local_date_time, author"),
    @Index(name = "idx_chat_entries_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEntryEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "timestamp", nullable = false)
    private String timestamp;
    
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
    
    @Column(name = "attachment_hash")
    private String attachmentHash;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (author == null || author.trim().isEmpty()) {
            author = "Unknown";
        }
    }
    
    // Convert from ChatEntry model to entity
    public static ChatEntryEntity fromChatEntry(ChatEntry chatEntry) {
        return ChatEntryEntity.builder()
                .timestamp(chatEntry.getTimestamp())
                .payload(chatEntry.getPayload())
                .author(chatEntry.getAuthor())
                .fileName(chatEntry.getFileName())
                .type(chatEntry.getType())
                .localDateTime(chatEntry.getLocalDateTime())
                .attachmentHash(chatEntry.getAttachmentHash())
                .build();
    }
    
    // Convert entity back to ChatEntry model
    public ChatEntry toChatEntry() {
        ChatEntry chatEntry = ChatEntry.builder()
                .timestamp(timestamp)
                .payload(payload)
                .author(author)
                .fileName(fileName)
                .type(type)
                .localDateTime(localDateTime)
                .build();
        chatEntry.setAttachmentHash(attachmentHash);
        return chatEntry;
    }
} 