package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    
    @Column(name = "client_id", nullable = false)
    private String clientId;
    
    @Column(name = "last_added_timestamp", nullable = false)
    private LocalDateTime lastAddedTimestamp;
    
    @Column(name = "status", nullable = false)
    private Byte status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;
    
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