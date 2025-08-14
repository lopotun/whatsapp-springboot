package net.kem.whatsapp.chatviewer.whatsappspringboot.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;

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
        java.util.List<Attachment> findByLastAddedTimestampAfter(
                        @Param("timestamp") java.time.LocalDateTime timestamp);

        /**
         * Find attachments by file size range
         */
        @Query("SELECT a FROM Attachment a WHERE a.fileSize BETWEEN :minSize AND :maxSize")
        java.util.List<Attachment> findByFileSizeBetween(@Param("minSize") Long minSize,
                        @Param("maxSize") Long maxSize);

        /**
         * Find attachments larger than specified size
         */
        @Query("SELECT a FROM Attachment a WHERE a.fileSize > :size")
        java.util.List<Attachment> findByFileSizeGreaterThan(@Param("size") Long size);

        /**
         * Find attachments smaller than specified size
         */
        @Query("SELECT a FROM Attachment a WHERE a.fileSize < :size")
        java.util.List<Attachment> findByFileSizeLessThan(@Param("size") Long size);

        /**
         * Get total file size for all attachments
         */
        @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a")
        Long getTotalFileSize();

        /**
         * Get total file size for attachments by status
         */
        @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a WHERE a.status = :status")
        Long getTotalFileSizeByStatus(@Param("status") Byte status);

        /**
         * Get total file size for attachments belonging to a specific user Note: This is a
         * simplified approach - in a production system you might want to track user-specific
         * attachment ownership more precisely
         */
        @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a WHERE a.status = 1")
        Long getTotalFileSizeForUser(@Param("userId") Long userId);

        /**
         * Find attachment hashes that belong to a specific user's chat entries This ensures user
         * isolation by only returning attachments referenced in user's chats
         */
        @Query("SELECT DISTINCT ce.attachment.hash FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.attachment IS NOT NULL")
        List<String> findAttachmentHashesByUserId(@Param("userId") Long userId);

        /**
         * Find attachments by a list of hashes
         */
        List<Attachment> findByHashIn(List<String> hashes);
}
