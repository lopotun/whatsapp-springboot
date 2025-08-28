package net.kem.whatsapp.chatviewer.zipper.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.shared.repository.AttachmentRepository;
import net.kem.whatsapp.chatviewer.shared.util.FileNamingService;
import net.kem.whatsapp.chatviewer.shared.model.AttachmentEntity;
import net.kem.whatsapp.chatviewer.zipper.model.ProcessingResult;
import net.kem.whatsapp.chatviewer.zipper.service.AttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of AttachmentService for the zipper service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_FILE_SIZE = 5 * 100 * 1024 * 1024; // 500MB limit

    @Value("${app.file-storage.base-path:/tmp/zipper-storage}")
    private String baseStoragePath;

    private final AttachmentRepository attachmentRepository;
    private final FileNamingService fileNamingService;

    @Override
    @Transactional
    public AttachmentEntity saveAttachment(AttachmentEntity attachment) {
        try {
            // Set creation timestamp if not already set
            if (attachment.getLastAddedTimestamp() == null) {
                attachment.setLastAddedTimestamp(LocalDateTime.now());
            }

            // Set status to active if not already set
            if (attachment.getStatus() == null) {
                attachment.setStatus((byte) 1);
            }

            AttachmentEntity savedAttachment = attachmentRepository.save(attachment);
            log.debug("Saved attachment with hash: {}, ID: {}", savedAttachment.getHash(), savedAttachment.getHash());
            return savedAttachment;
        } catch (Exception e) {
            log.error("Failed to save attachment with hash: {} - {}", attachment.getHash(), e.getMessage(), e);
            throw new RuntimeException("Failed to save attachment", e);
        }
    }

    @Override
    public Optional<AttachmentEntity> findByHash(String hash) {
        try {
            Optional<AttachmentEntity> attachment = attachmentRepository.findByHash(hash);
            if (attachment.isPresent()) {
                log.debug("Found attachment with hash: {} and ID: {}", hash, attachment.get().getHash());
            } else {
                log.debug("No attachment found with hash: {}", hash);
            }
            return attachment;
        } catch (Exception e) {
            log.error("Failed to find attachment by hash: {} - {}", hash, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByHash(String hash) {
        try {
            boolean exists = attachmentRepository.existsByHash(hash);
            log.debug("Attachment with hash {} exists: {}", hash, exists);
            return exists;
        } catch (Exception e) {
            log.error("Failed to check if attachment exists with hash: {} - {}", hash, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Pair<AttachmentEntity, ProcessingResult.ProcessedAttachment> storeFileWithHash(InputStream inputStream, String fileName, long size) {
        log.debug("Processing multimedia file: {}", fileName);

        Path tempFile = null;
        try {
            // Use larger buffer for better I/O performance
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            // Stream-based hash calculation to avoid loading entire file into memory
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Start with ByteArrayOutputStream, switch to temp file if needed
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean usingTempFile = false;
            final int MEMORY_THRESHOLD = 100 * 1024; // 100KB threshold

            // Read file content and calculate hash in one pass
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Check file size limit early
                totalBytes += bytesRead;
                if (totalBytes > MAX_FILE_SIZE) {
                    log.warn("Multimedia file too large: {} ({} bytes)", fileName, totalBytes);
                    return null;
                }

                // Update hash calculation
                digest.update(buffer, 0, bytesRead);

                // Handle memory vs file storage
                if (!usingTempFile && baos.size() + bytesRead > MEMORY_THRESHOLD) {
                    // Switch to temp file
                    try {
                        tempFile = Files.createTempFile("whatsapp_upload_", ".tmp");
                        log.debug("Switching to temp file for large multimedia: {} -> {}", fileName, tempFile);

                        // Write existing data to temp file
                        Files.write(tempFile, baos.toByteArray());
                        baos = null; // Release memory
                        usingTempFile = true;

                        // Append current buffer to temp file
                        byte[] bufferToWrite = new byte[bytesRead];
                        System.arraycopy(buffer, 0, bufferToWrite, 0, bytesRead);
                        Files.write(tempFile, bufferToWrite, StandardOpenOption.APPEND);

                    } catch (IOException e) {
                        log.error("Failed to create temp file for multimedia: {}", fileName, e);
                        throw new RuntimeException("Failed to create temp file", e);
                    }
                } else if (usingTempFile) {
                    // Continue writing to temp file
                    try (FileOutputStream fos = new FileOutputStream(tempFile.toFile(), true)) {
                        fos.write(buffer, 0, bytesRead);
                    } catch (IOException e) {
                        log.error("Failed to write to temp file for multimedia: {}", fileName, e);
                        throw new RuntimeException("Failed to write to temp file", e);
                    }
                } else {
                    // Continue using memory
                    baos.write(buffer, 0, bytesRead);
                }
            }

            // Convert hash to hex string efficiently
            String contentHash = bytesToHex(digest.digest());

            // Determine final file path
            Path finalFilePath = fileNamingService.generateFilePathFromHash(contentHash, fileName);
            boolean finalFileExists = Files.exists(finalFilePath);

            if (finalFileExists) {
                log.debug("File already exists, skipping save: {} (hash: {})", fileName, contentHash);
                // Temp file will be cleaned up in finally block
            } else {
                // Final file doesn't exist, we need to save it
                if (usingTempFile) {
                    // Rename temp file to final location
                    try {
                        // Ensure parent directory exists
                        Path parentDir = finalFilePath.getParent();
                        if (parentDir != null && !Files.exists(parentDir)) {
                            Files.createDirectories(parentDir);
                            log.debug("Created directory: {}", parentDir);
                        }

                        Files.move(tempFile, finalFilePath);
                        tempFile = null; // Successfully moved - prevent cleanup in finally block
                        log.info("Moved temp file to final location: {} -> {} (size: {} bytes)",
                            fileName, finalFilePath, totalBytes);
                    } catch (IOException e) {
                        log.error("Failed to move temp file to final location: {} -> {}", tempFile, finalFilePath, e);
                        throw new RuntimeException("Failed to move temp file", e);
                    }
                } else {
                    // Save from memory
                    saveFileToSystem(finalFilePath, baos.toByteArray(), fileName, totalBytes);
                }
            }
            String mimeType = determineMimeType(fileName);
            AttachmentEntity attachment = createAttachmentRecord(contentHash, finalFilePath, mimeType, totalBytes);
            ProcessingResult.ProcessedAttachment processedAttachment = ProcessingResult.ProcessedAttachment.builder()
                .originalFilename(fileName)
                .contentHash(contentHash)
                .filePath(finalFilePath.toString())
                .fileSize(totalBytes)
                .mimeType(mimeType)
                .build();
            return Pair.of(attachment, processedAttachment);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            log.error("Error processing multimedia file: {} - {}", fileName, e.getMessage());
            throw new RuntimeException("Error processing multimedia file: " + fileName, e);
        } finally {
            // Always clean up temp file if it still exists
            if (tempFile != null) {
                try {
                    boolean deleted = Files.deleteIfExists(tempFile);
                    if (deleted) {
                        log.debug("Cleaned up temp file: {}", tempFile);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp file during cleanup: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Determine MIME type based on file extension
     */
    private String determineMimeType(String fileName) {
        if (fileName == null)
            return "application/octet-stream";

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg"))
            return "image/jpeg";
        if (lowerFileName.endsWith(".png"))
            return "image/png";
        if (lowerFileName.endsWith(".gif"))
            return "image/gif";
        if (lowerFileName.endsWith(".mp4"))
            return "video/mp4";
        if (lowerFileName.endsWith(".mp3"))
            return "audio/mpeg";
        if (lowerFileName.endsWith(".pdf"))
            return "application/pdf";
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx"))
            return "application/msword";

        return "application/octet-stream";
    }

    /**
     * Create an attachment database record
     */
    private AttachmentEntity createAttachmentRecord(String hash, Path finalFilePath, String mimeType, long size) {
        return AttachmentEntity.builder()
            .hash(hash)
            .fileSize(size)
            .mimeType(mimeType)
            .col1(finalFilePath.getFileName().toString()) // Store original filename
            .col2(finalFilePath.toString()) // Store file path
            .status((byte) 1) // 1 = active
            .build();
    }

    /**
     * Convert byte array to hex string efficiently
     */
    // private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private String bytesToHex(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Save file to filesystem with proper error handling
     */
    private void saveFileToSystem(Path filePath, byte[] fileContent, String fileName, long totalBytes) {
        try {
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("Created directory: {}", parentDir);
            }

            // Write the file content atomically
            Files.write(filePath, fileContent);
            log.info("Saved multimedia file: {} to path: {} (size: {} bytes)", fileName, filePath, totalBytes);

        } catch (Exception e) {
            log.error("Failed to save multimedia file to filesystem: {} - {}", fileName, e.getMessage());
            // Don't throw exception, continue processing
        }
    }

    @Override
    public String getFilePathByHash(String hash) {
        Path filePath = createFilePath(hash);
        return filePath.toString();
    }

    /**
     * Create the file path using hierarchical directory structure Format:
     * basePath/hash.substring(0,3)/hash.substring(3,6)/hash Note: No user isolation - all users
     * share the same attachment storage
     */
    private Path createFilePath(String hash) {
        if (hash.length() < 6) {
            throw new IllegalArgumentException("Hash must be at least 6 characters long");
        }

        String level1Dir = hash.substring(0, 3);
        String level2Dir = hash.substring(3, 6);

        return Paths.get(baseStoragePath, level1Dir, level2Dir, hash);
    }
}
