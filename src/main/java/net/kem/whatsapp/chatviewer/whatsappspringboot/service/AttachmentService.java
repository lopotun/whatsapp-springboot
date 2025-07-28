package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.AttachmentRepository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.LocationRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class AttachmentService {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LocationRepository locationRepository;

    /**
     * Save or update attachment and create location record
     *
     * @param hash The SHA-256 hash of the file content
     * @param realFilename The original filename
     * @param userId The user ID this file belongs to
     * @return The created or updated attachment
     */
    public Attachment saveAttachmentWithLocation(String hash, String realFilename, Long userId) {
        return saveAttachmentWithLocation(hash, realFilename, userId, null);
    }

    /**
     * Save or update attachment and create location record with file size
     *
     * @param hash The SHA-256 hash of the file content
     * @param realFilename The original filename
     * @param userId The user ID this file belongs to
     * @param fileSize The size of the file in bytes
     * @return The created or updated attachment
     */
    public Attachment saveAttachmentWithLocation(String hash, String realFilename, Long userId,
            Long fileSize) {
        // Check if attachment already exists
        Optional<Attachment> existingAttachment = attachmentRepository.findByHash(hash);
        Attachment attachment;

        if (existingAttachment.isPresent()) {
            // Update existing attachment timestamp and file size if provided
            attachment = existingAttachment.get();
            attachment.setLastAddedTimestamp(LocalDateTime.now());
            if (fileSize != null) {
                attachment.setFileSize(fileSize);
            }
            attachment = attachmentRepository.save(attachment);
            log.debug("Updated existing attachment with hash: {}", hash);
        } else {
            // Create new attachment
            attachment = Attachment.builder().hash(hash).lastAddedTimestamp(LocalDateTime.now())
                    .status((byte) 1).fileSize(fileSize).build();
            attachment = attachmentRepository.save(attachment);
            log.info("Created new attachment with hash: {} and size: {} bytes", hash, fileSize);
        }

        // Check if location already exists for this user and filename
        Optional<Location> existingLocation =
                locationRepository.findByRealFilenameAndUserId(realFilename, userId);

        if (existingLocation.isPresent()) {
            // Update existing location
            Location location = existingLocation.get();
            location.setLastAddedTimestamp(LocalDateTime.now());
            location.setStatus((byte) 1);
            locationRepository.save(location);
            log.debug("Updated existing location for file: {} and user: {}", realFilename, userId);
        } else {
            // Create new location
            Location location = Location.builder().realFilename(realFilename).userId(userId)
                    .lastAddedTimestamp(LocalDateTime.now()).status((byte) 1).attachment(attachment)
                    .build();
            locationRepository.save(location);
            log.info("Created new location for file: {} and user: {}", realFilename, userId);
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
     * Find attachment IDs by user ID
     */
    public List<Long> findAttachmentIdsByUserId(Long userId) {
        return locationRepository.findAttachmentIdsByUserId(userId);
    }

    /**
     * Find chat entry IDs by user ID
     */
    public List<Long> findChatEntryIdsByUserId(Long userId) {
        return locationRepository.findChatEntryIdsByUserId(userId);
    }

    /**
     * Find all locations for a user
     */
    public List<Location> findLocationsByUserId(Long userId) {
        return locationRepository.findByUserId(userId);
    }

    /**
     * Find locations by user ID and status
     */
    public List<Location> findLocationsByUserIdAndStatus(Long userId, Byte status) {
        return locationRepository.findByUserIdAndStatus(userId, status);
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
     * Find locations after timestamp
     */
    public List<Location> findLocationsAfterTimestamp(LocalDateTime timestamp) {
        return locationRepository.findByLastAddedTimestampAfter(timestamp);
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
     * Update location status
     */
    public Location updateLocationStatus(Long locationId, Byte status) {
        Optional<Location> location = locationRepository.findById(locationId);
        if (location.isPresent()) {
            Location loc = location.get();
            loc.setStatus(status);
            return locationRepository.save(loc);
        } else {
            throw new IllegalArgumentException("Location not found with ID: " + locationId);
        }
    }

    /**
     * Save location for chat entry
     *
     * @param realFilename The original filename
     * @param userId The user ID this file belongs to
     * @param chatEntry The chat entry this location belongs to
     * @return The created location
     */
    public Location saveLocationForChatEntry(String realFilename, Long userId,
            net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity chatEntry) {
        // Find the attachment by looking for a location with this filename
        Optional<Location> existingLocationWithFilename =
                locationRepository.findByRealFilenameAndUserId(realFilename, userId);

        Attachment attachment = null;
        if (existingLocationWithFilename.isPresent()) {
            // Use the existing location's attachment
            attachment = existingLocationWithFilename.get().getAttachment();
        }

        if (attachment == null) {
            log.warn("No attachment found for filename: {} and user: {}", realFilename, userId);
            return null;
        }

        // Check if location already exists for this chat entry and filename
        Optional<Location> existingLocationForChatEntry =
                locationRepository.findByRealFilenameAndUserId(realFilename, userId);

        if (existingLocationForChatEntry.isPresent()) {
            // Update existing location to link it to this chat entry
            Location location = existingLocationForChatEntry.get();
            location.setLastAddedTimestamp(LocalDateTime.now());
            location.setStatus((byte) 1);
            location.setChatEntry(chatEntry);
            location.setAttachment(attachment);
            location.setUserId(userId);
            locationRepository.save(location);
            log.debug("Updated existing location for file: {} and chat entry: {}", realFilename,
                    chatEntry.getId());
            return location;
        } else {
            // Create new location linking the chat entry to the attachment
            Location location = Location.builder().realFilename(realFilename).userId(userId)
                    .lastAddedTimestamp(LocalDateTime.now()).status((byte) 1).chatEntry(chatEntry)
                    .attachment(attachment).build();
            locationRepository.save(location);
            log.info("Created new location for file: {} and chat entry: {}", realFilename,
                    chatEntry.getId());
            return location;
        }
    }
}
