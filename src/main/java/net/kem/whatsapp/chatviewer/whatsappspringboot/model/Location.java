package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "real_filename", nullable = false)
    private String realFilename;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "last_added_timestamp", nullable = false)
    private LocalDateTime lastAddedTimestamp;

    @Column(name = "status", nullable = false)
    private Byte status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    @JsonIgnore
    private Attachment attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_entry_id")
    @JsonIgnore
    private ChatEntryEntity chatEntry;

    @PrePersist
    protected void onCreate() {
        if (lastAddedTimestamp == null) {
            lastAddedTimestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = 1; // Default active status
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastAddedTimestamp = LocalDateTime.now();
    }
}
