package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

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

    @PrePersist
    protected void onCreate() {
        if (lastAddedTimestamp == null) {
            lastAddedTimestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = 1; // Default active status
        }
    }
}
