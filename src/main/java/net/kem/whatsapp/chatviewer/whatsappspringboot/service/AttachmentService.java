package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.AttachmentRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class AttachmentService {

    @Autowired
    private AttachmentRepository attachmentRepository;

    /**
     * Save or update attachment
     *
     * @param hash The SHA-256 hash of the file content
     * @param fileSize The size of the file in bytes
     * @return The created or updated attachment
     */
    public Attachment saveAttachment(String hash, Long fileSize) {
        // Check if attachment already exists
        Optional<Attachment> existingAttachment = attachmentRepository.findByHash(hash);
        Attachment attachment;

        if (existingAttachment.isPresent()) {
            // Update existing attachment timestamp and file size if provided
            attachment = existingAttachment.get();
            attachment.setLastAddedTimestamp(LocalDateTime.now());
            if (fileSize != null) {
                // Only update file size if it is provided and different from existing
                if (!attachment.getFileSize().equals(fileSize)) {
                    attachment.setFileSize(fileSize);
                    log.warn("New file size {} <> {} (existing) for attachment with hash {}",
                        fileSize, attachment.getFileSize(), hash);
                }
            }
            attachment = attachmentRepository.save(attachment);
            log.debug("Updated existing attachment with hash: {}", hash);
        } else {
            // Create new attachment
            attachment = Attachment.builder()
                    .hash(hash)
                    .lastAddedTimestamp(LocalDateTime.now())
                    .status((byte) 1)
                    .fileSize(fileSize)
                    .build();
            attachment = attachmentRepository.save(attachment);
            log.info("Created new attachment with hash: {} and size: {} bytes", hash, fileSize);
        }
        return attachment;
    }

    /**
     * Find attachment by hash
     */
    public Optional<Attachment> findByHash(String hash) {
        return attachmentRepository.findByHash(hash);
    }

    /**
     * Find attachment by ID
     */
    public Optional<Attachment> findById(Long id) {
        return attachmentRepository.findById(id);
    }

    /**
     * Check if attachment exists by hash
     */
    public boolean existsByHash(String hash) {
        return attachmentRepository.existsByHash(hash);
    }

    /**
     * Find all attachments
     */
    public List<Attachment> findAllAttachments() {
        return attachmentRepository.findAll();
    }

    /**
     * Find attachments by status
     */
    public List<Attachment> findAttachmentsByStatus(Byte status) {
        return attachmentRepository.findByStatus(status);
    }

    /**
     * Find attachments after timestamp
     */
    public List<Attachment> findAttachmentsAfterTimestamp(LocalDateTime timestamp) {
        return attachmentRepository.findByLastAddedTimestampAfter(timestamp);
    }

    /**
     * Find attachments by file size range
     */
    public List<Attachment> findAttachmentsByFileSizeRange(Long minSize, Long maxSize) {
        return attachmentRepository.findByFileSizeBetween(minSize, maxSize);
    }

    /**
     * Find attachments larger than specified size
     */
    public List<Attachment> findAttachmentsLargerThan(Long size) {
        return attachmentRepository.findByFileSizeGreaterThan(size);
    }

    /**
     * Find attachments smaller than specified size
     */
    public List<Attachment> findAttachmentsSmallerThan(Long size) {
        return attachmentRepository.findByFileSizeLessThan(size);
    }

    /**
     * Get total file size for all attachments
     */
    public Long getTotalFileSize() {
        return attachmentRepository.getTotalFileSize();
    }

    /**
     * Get total file size for attachments by status
     */
    public Long getTotalFileSizeByStatus(Byte status) {
        return attachmentRepository.getTotalFileSizeByStatus(status);
    }

    /**
     * Update attachment status
     */
    public Attachment updateAttachmentStatus(String hash, Byte status) {
        Optional<Attachment> attachment = attachmentRepository.findByHash(hash);
        if (attachment.isPresent()) {
            Attachment att = attachment.get();
            att.setStatus(status);
            return attachmentRepository.save(att);
        } else {
            throw new IllegalArgumentException("Attachment not found with hash: " + hash);
        }
    }

    /**
     * Generate file path for attachment based on hash
     * Creates hierarchical directory structure: hash.substring(0,3)/hash.substring(3,6)/hash
     */
    public String generateFilePath(String hash) {
        if (hash == null || hash.length() < 6) {
            return null;
        }
        return hash.substring(0, 3) + "/" + hash.substring(3, 6) + "/" + hash;
    }
}
