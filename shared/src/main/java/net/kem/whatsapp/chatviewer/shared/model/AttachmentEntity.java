package net.kem.whatsapp.chatviewer.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Attachment entity for the zipper service Maps to the attachments table in the database Must match
 * the main service's Attachment entity structure exactly
 */
@Entity
@Table(name = "attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash", nullable = false, unique = true, length = 64)
    private String hash;

    @Column(name = "last_added_timestamp", nullable = false)
    private LocalDateTime lastAddedTimestamp;

    @Column(name = "status", nullable = false)
    private Byte status;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "col1")
    private String col1;

    @Column(name = "col2")
    private String col2;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "original_filename")
    private String originalFilename;

    // One-to-many relationship with chat entries
    @OneToMany(mappedBy = "attachment", fetch = FetchType.LAZY)
    private List<ChatEntryEntity> chatEntries;

    @PrePersist
    protected void onCreate() {
        if (lastAddedTimestamp == null) {
            lastAddedTimestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = 1; // Default active status
        }
    }

    /**
     * Check if attachment is active
     */
    public boolean isActive() {
        return status != null && status == 1;
    }

    /**
     * Check if attachment has a valid hash
     */
    public boolean hasValidHash() {
        return hash != null && hash.length() == 64; // SHA-256 hash length
    }
}
