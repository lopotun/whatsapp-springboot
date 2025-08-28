package net.kem.whatsapp.chatviewer.zipper.service;

import net.kem.whatsapp.chatviewer.shared.model.AttachmentEntity;
import net.kem.whatsapp.chatviewer.zipper.model.ProcessingResult;
import org.springframework.data.util.Pair;

import java.io.InputStream;

/**
 * Service for managing attachment database operations in the zipper service
 */
public interface AttachmentService {

    /**
     * Save an attachment to the database
     */
    AttachmentEntity saveAttachment(AttachmentEntity attachment);

    /**
     * Find attachment by hash
     */
    java.util.Optional<AttachmentEntity> findByHash(String hash);

    /**
     * Check if attachment exists by hash
     */
    boolean existsByHash(String hash);

    /**
     * Store a file with hash calculation and return the hash Also creates an Attachment database
     * record
     */
    Pair<AttachmentEntity, ProcessingResult.ProcessedAttachment> storeFileWithHash(InputStream inputStream, String filename, long size);

    /**
     * Get file path by hash
     */
    String getFilePathByHash(String hash);
}
