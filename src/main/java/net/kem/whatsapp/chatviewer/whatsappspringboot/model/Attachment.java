package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Attachments")
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
    
    @Column(name = "col1")
    private String col1;
    
    @Column(name = "col2")
    private String col2;
    
    @OneToMany(mappedBy = "attachment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Location> locations;
    
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