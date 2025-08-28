package net.kem.whatsapp.chatviewer.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shared ChatEntry model for WhatsApp chat data
 * Used by both main service and zipper service for inter-service communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEntry {
    private String payload;
    private String author;
    private String fileName;
    private Type type; // String type for flexibility between services
    private LocalDateTime localDateTime;
    private String attachmentHash; // For linking to attachments
    private Long userId;
    private String chatId;

    /**
     * Check if this entry has an attachment
     */
    public boolean hasAttachment() {
        return attachmentHash != null && !attachmentHash.trim().isEmpty();
    }

    /**
     * Check if this is a text message
     */
    public boolean isTextMessage() {
        return "TEXT".equals(type);
    }

    /**
     * Check if this is a multimedia message
     */
    public boolean isMultimediaMessage() {
        return type != null && !"TEXT".equals(type);
    }

    public enum Type {
        TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, LOCATION, CONTACT, POLL, STICKER, REMOVED_MEDIA, UNKNOWN
    }
}
