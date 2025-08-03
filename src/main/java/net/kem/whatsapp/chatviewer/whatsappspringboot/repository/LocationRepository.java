package net.kem.whatsapp.chatviewer.whatsappspringboot.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

        /**
         * Find locations by user ID
         */
        List<Location> findByUserId(Long userId);

        /**
         * Find locations by attachment ID
         */
        List<Location> findByAttachmentId(Long attachmentId);

        /**
         * Find locations by chat entry ID
         */
        List<Location> findByChatEntryId(Long chatEntryId);

        /**
         * Find locations by chat entry ID and user ID (user-scoped)
         */
        List<Location> findByChatEntryIdAndUserId(Long chatEntryId, Long userId);

        /**
         * Find attachment IDs by user ID
         */
        @Query("SELECT DISTINCT l.attachment.id FROM Location l WHERE l.userId = :userId")
        List<Long> findAttachmentIdsByUserId(@Param("userId") Long userId);

        /**
         * Find chat entry IDs by user ID
         */
        @Query("SELECT DISTINCT l.chatEntry.id FROM Location l WHERE l.userId = :userId")
        List<Long> findChatEntryIdsByUserId(@Param("userId") Long userId);

        /**
         * Find location by real filename and user ID
         */
        Optional<Location> findByRealFilenameAndUserId(String realFilename, Long userId);

        /**
         * Find locations by status
         */
        @Query("SELECT l FROM Location l WHERE l.status = :status")
        List<Location> findByStatus(@Param("status") Byte status);

        /**
         * Find locations for a specific user with given status
         */
        @Query("SELECT l FROM Location l WHERE l.userId = :userId AND l.status = :status")
        List<Location> findByUserIdAndStatus(@Param("userId") Long userId,
                        @Param("status") Byte status);

        /**
         * Find locations updated after a specific timestamp
         */
        @Query("SELECT l FROM Location l WHERE l.lastAddedTimestamp >= :timestamp")
        List<Location> findByLastAddedTimestampAfter(
                        @Param("timestamp") java.time.LocalDateTime timestamp);
}
