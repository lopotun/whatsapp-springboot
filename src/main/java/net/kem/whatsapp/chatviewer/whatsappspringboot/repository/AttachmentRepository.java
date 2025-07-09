package net.kem.whatsapp.chatviewer.whatsappspringboot.repository;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    
    /**
     * Find attachment by hash
     */
    Optional<Attachment> findByHash(String hash);
    
    /**
     * Check if attachment exists by hash
     */
    boolean existsByHash(String hash);
    
    /**
     * Find attachments by status
     */
    @Query("SELECT a FROM Attachment a WHERE a.status = :status")
    java.util.List<Attachment> findByStatus(@Param("status") Byte status);
    
    /**
     * Find attachments created after a specific timestamp
     */
    @Query("SELECT a FROM Attachment a WHERE a.lastAddedTimestamp >= :timestamp")
    java.util.List<Attachment> findByLastAddedTimestampAfter(@Param("timestamp") java.time.LocalDateTime timestamp);
} 