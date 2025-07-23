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
         * Find locations by client ID
         */
        List<Location> findByClientId(String clientId);

        /**
         * Find attachment IDs by client ID
         */
        @Query("SELECT l.attachment.id FROM Location l WHERE l.clientId = :clientId")
        List<Long> findAttachmentIdsByClientId(@Param("clientId") String clientId);



        /**
         * Find locations by attachment ID
         */
        List<Location> findByAttachmentId(Long attachmentId);

        /**
         * Find location by real filename and client ID
         */
        Optional<Location> findByRealFilenameAndClientId(String realFilename, String clientId);

        /**
         * Find locations by status
         */
        @Query("SELECT l FROM Location l WHERE l.status = :status")
        List<Location> findByStatus(@Param("status") Byte status);

        /**
         * Find locations for a specific client with given status
         */
        @Query("SELECT l FROM Location l WHERE l.clientId = :clientId AND l.status = :status")
        List<Location> findByClientIdAndStatus(@Param("clientId") String clientId,
                        @Param("status") Byte status);

        /**
         * Find locations updated after a specific timestamp
         */
        @Query("SELECT l FROM Location l WHERE l.lastAddedTimestamp >= :timestamp")
        List<Location> findByLastAddedTimestampAfter(
                        @Param("timestamp") java.time.LocalDateTime timestamp);
}
