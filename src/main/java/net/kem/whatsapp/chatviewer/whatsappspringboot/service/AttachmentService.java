package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.AttachmentRepository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
     * @param clientId The client ID this file belongs to
     * @return The created or updated attachment
     */
    public Attachment saveAttachmentWithLocation(String hash, String realFilename, String clientId) {
        // Check if attachment already exists
        Optional<Attachment> existingAttachment = attachmentRepository.findByHash(hash);
        Attachment attachment;
        
        if (existingAttachment.isPresent()) {
            // Update existing attachment timestamp
            attachment = existingAttachment.get();
            attachment.setLastAddedTimestamp(LocalDateTime.now());
            attachment = attachmentRepository.save(attachment);
            log.debug("Updated existing attachment with hash: {}", hash);
        } else {
            // Create new attachment
            attachment = Attachment.builder()
                    .hash(hash)
                    .lastAddedTimestamp(LocalDateTime.now())
                    .status((byte) 1)
                    .build();
            attachment = attachmentRepository.save(attachment);
            log.info("Created new attachment with hash: {}", hash);
        }
        
        // Check if location already exists for this client and filename
        Optional<Location> existingLocation = locationRepository.findByRealFilenameAndClientId(realFilename, clientId);
        
        if (existingLocation.isPresent()) {
            // Update existing location
            Location location = existingLocation.get();
            location.setLastAddedTimestamp(LocalDateTime.now());
            location.setStatus((byte) 1);
            locationRepository.save(location);
            log.debug("Updated existing location for file: {} and client: {}", realFilename, clientId);
        } else {
            // Create new location
            Location location = Location.builder()
                    .realFilename(realFilename)
                    .clientId(clientId)
                    .lastAddedTimestamp(LocalDateTime.now())
                    .status((byte) 1)
                    .attachment(attachment)
                    .build();
            locationRepository.save(location);
            log.info("Created new location for file: {} and client: {}", realFilename, clientId);
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
     * Check if attachment exists by hash
     */
    public boolean existsByHash(String hash) {
        return attachmentRepository.existsByHash(hash);
    }
    
    /**
     * Find all locations for a client
     */
    public List<Location> findLocationsByClientId(String clientId) {
        return locationRepository.findByClientId(clientId);
    }
    
    /**
     * Find all locations for a client with specific status
     */
    public List<Location> findLocationsByClientIdAndStatus(String clientId, Byte status) {
        return locationRepository.findByClientIdAndStatus(clientId, status);
    }
    
    /**
     * Find all attachments with specific status
     */
    public List<Attachment> findAttachmentsByStatus(Byte status) {
        return attachmentRepository.findByStatus(status);
    }
    
    /**
     * Find attachments created after a specific timestamp
     */
    public List<Attachment> findAttachmentsAfterTimestamp(LocalDateTime timestamp) {
        return attachmentRepository.findByLastAddedTimestampAfter(timestamp);
    }
    
    /**
     * Find locations updated after a specific timestamp
     */
    public List<Location> findLocationsAfterTimestamp(LocalDateTime timestamp) {
        return locationRepository.findByLastAddedTimestampAfter(timestamp);
    }
    
    /**
     * Update attachment status
     */
    public Attachment updateAttachmentStatus(String hash, Byte status) {
        Optional<Attachment> attachmentOpt = attachmentRepository.findByHash(hash);
        if (attachmentOpt.isPresent()) {
            Attachment attachment = attachmentOpt.get();
            attachment.setStatus(status);
            return attachmentRepository.save(attachment);
        }
        throw new IllegalArgumentException("Attachment not found with hash: " + hash);
    }
    
    /**
     * Update location status
     */
    public Location updateLocationStatus(Long locationId, Byte status) {
        Optional<Location> locationOpt = locationRepository.findById(locationId);
        if (locationOpt.isPresent()) {
            Location location = locationOpt.get();
            location.setStatus(status);
            return locationRepository.save(location);
        }
        throw new IllegalArgumentException("Location not found with ID: " + locationId);
    }
} 