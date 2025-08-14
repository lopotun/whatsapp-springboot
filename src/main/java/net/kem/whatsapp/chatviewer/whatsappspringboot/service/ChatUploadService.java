package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatUploadService {

    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s\\d{1,2}:\\d{2})\\s-\\s");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("M/d/yy, HH:mm");
    private static final long MAX_FILE_SIZE = 5 * 100 * 1024 * 1024; // 500MB limit
    private static final int MAX_ENTRIES = 1000; // Limit number of entries to prevent infinite
    private static final int UPLOAD_REQUEST_TIMEOUT = 20 * 60 * 1000; // Limit number of entries per
    private static final int MAX_ENTRIES_PER_ZIP = 1000; // Limit number of entries per zip to

    private final ChatEntryService chatEntryService;
    private final ChatService chatService;
    private final FileNamingService fileNamingService;
    private final AttachmentService attachmentService;

    // Progress tracking for async uploads
    private final ConcurrentHashMap<String, SseEmitter> progressEmitters =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UploadProgress> uploadProgress =
            new ConcurrentHashMap<>();

    /**
     * Generate a unique chat ID based on the original filename and user ID
     */
    public String generateChatId(String originalFileName, Long userId) {
        String baseName = originalFileName;
        // Remove file extension if present
        if (originalFileName.contains(".")) {
            baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        }

        // Remove any non-alphanumeric characters and replace with underscores
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "_");

        // Add timestamp to ensure uniqueness
        // String timestamp =
        // LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Add user ID to prevent conflicts between users
        // return String.format("user%d_%s_%s", userId, baseName, timestamp);
        return String.format("user%d_%s", userId, baseName);
    }

    /**
     * Upload and process a text file containing WhatsApp chat data
     */
    @Transactional
    public UploadResult uploadTextFile(MultipartFile file, Long userId) {
        log.info("Starting text file upload for user: {} with file: {}", userId,
                file.getOriginalFilename());

        UploadResult.UploadResultBuilder resultBuilder = UploadResult.builder()
                .originalFileName(file.getOriginalFilename()).fileType("text").success(false);

        try {
            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                String errorMsg =
                        "File size exceeds maximum allowed size of " + MAX_FILE_SIZE + " bytes";
                log.warn("File size validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Validate file size
            if (!StringUtils.hasText(file.getOriginalFilename())) {
                String errorMsg = "No file name was supplied";
                log.warn("File name validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Generate unique chat ID
            String chatId = generateChatId(file.getOriginalFilename(), userId);
            resultBuilder.chatId(chatId);

            // Parse chat entries from the text file
            Set<ChatEntry> chatEntries = new LinkedHashSet<>(512);
            Map<String, String> filenameToHashMap = new HashMap<>();

            try (InputStream inputStream = file.getInputStream();
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                StringBuilder currentEntry = new StringBuilder();
                final AtomicInteger entryCount = new AtomicInteger();

                while ((line = reader.readLine()) != null && entryCount.get() < MAX_ENTRIES) {
                    if (isNewEntry(line)) {
                        // Process the previous entry if it exists
                        if (!currentEntry.isEmpty()) {
                            parseChatEntry(currentEntry.toString()).ifPresent(chatEntry -> {
                                chatEntries.add(chatEntry);
                                entryCount.getAndIncrement();
                            });
                        }
                        // Start new entry
                        currentEntry = new StringBuilder(line);
                    } else {
                        // Continue the current entry
                        currentEntry.append("\n").append(line);
                    }
                }

                // Process the last entry
                if (!currentEntry.isEmpty() && entryCount.get() < MAX_ENTRIES) {
                    parseChatEntry(currentEntry.toString()).ifPresent(chatEntry -> {
                        chatEntries.add(chatEntry);
                        entryCount.getAndIncrement();
                    });
                }
            }

            // Remove any duplicate entries that might have been parsed
            log.info("Parsed {} chat entries, removing duplicates...", chatEntries.size());
            List<ChatEntry> deduplicatedEntries = deduplicateEntries(new ArrayList<>(chatEntries));
            log.info("After deduplication: {} unique entries", deduplicatedEntries.size());

            log.info("Parsed {} chat entries from text file for user: {}",
                    deduplicatedEntries.size(), userId);

            // Save to database
            List<ChatEntryEntity> savedEntries = performIncrementalUpdate(
                    new LinkedHashSet<>(deduplicatedEntries), userId, chatId, filenameToHashMap);

            resultBuilder.totalEntries(savedEntries.size())
                    .totalAttachments(filenameToHashMap.size()).success(true);

            log.info("Successfully uploaded text file for user: {} - {} entries, {} attachments",
                    userId, savedEntries.size(), filenameToHashMap.size());
            return resultBuilder.build();
        } catch (Exception e) {
            // Create detailed error information
            UploadError error = createUploadError(e, "Text file processing");
            String errorMsg = error.getUserMessage();

            log.error(
                    "Text file upload failed for user: {} - Error Code: {}, Message: {}, Technical: {}",
                    userId, error.getErrorCode(), errorMsg, error.getTechnicalDetails(), e);

            return resultBuilder.errorMessage(errorMsg).build();
        }
    }

    /**
     * Upload and process a ZIP file from file path (for async processing)
     */
    public UploadResult uploadZipFileFromPath(Path filePath, String fileName, Long userId) {
        log.info("Starting ZIP file processing from path for user: {} with file: {}", userId,
                fileName);

        UploadResult.UploadResultBuilder resultBuilder =
                UploadResult.builder().originalFileName(fileName).fileType("zip").success(false);

        try {
            // Validate file size
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                String errorMsg =
                        "File size exceeds maximum allowed size of " + MAX_FILE_SIZE + " bytes";
                log.warn("File size validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Validate zip file name
            if (!StringUtils.hasText(fileName)) {
                String errorMsg = "No file name was supplied";
                log.warn("File name validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Generate unique chat ID
            String chatId = generateChatId(fileName, userId);
            resultBuilder.chatId(chatId);

            Set<ChatEntry> chatEntries = new LinkedHashSet<>(512);
            Map<String, String> filenameToChecksum = new HashMap<>();
            List<String> extractedFiles = new ArrayList<>();

            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(Files.newInputStream(filePath), 80 * 1024))) {
                ZipEntry entry;
                int entryCount = 0;

                while ((entry = zis.getNextEntry()) != null && entryCount < MAX_ENTRIES_PER_ZIP) {
                    String entryFileName = entry.getName();
                    extractedFiles.add(entryFileName);

                    try {
                        if (isChatTextFile(entryFileName)) {
                            // Process text file (chat data)
                            processChatTextStream(zis, chatEntries);
                            entryCount++;
                        } else {
                            // Process multimedia file
                            String contentHash = processMultimediaFile(zis, entryFileName, userId);
                            if (contentHash != null) {
                                filenameToChecksum.put(entryFileName, contentHash);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing ZIP entry: {} - {}", entryFileName,
                                e.getMessage());
                        // Continue processing other entries even if one fails
                    } finally {
                        // Ensure the current entry is closed and ready for the next one
                        try {
                            zis.closeEntry();
                        } catch (IOException e) {
                            log.warn("Error closing ZIP entry: {} - {}", entryFileName,
                                    e.getMessage());
                        }
                    }
                }
            }
            // Remove any duplicate entries that might have been parsed
            log.info("Processed ZIP file for user: {} - {} chat entries, removing duplicates...",
                    userId, chatEntries.size());
            List<ChatEntry> deduplicatedEntries = deduplicateEntries(new ArrayList<>(chatEntries));
            log.info("After deduplication: {} unique entries", deduplicatedEntries.size());

            log.info("Processed ZIP file for user: {} - {} chat entries, {} attachments", userId,
                    deduplicatedEntries.size(), filenameToChecksum.size());

            // Save to database
            log.info("Starting database save for user: {} and chat: {}", userId, chatId);
            List<ChatEntryEntity> savedEntries = performIncrementalUpdate(
                    new LinkedHashSet<>(deduplicatedEntries), userId, chatId, filenameToChecksum);

            resultBuilder.totalEntries(savedEntries.size())
                    .totalAttachments(filenameToChecksum.size()).extractedFiles(extractedFiles)
                    .success(true);
            log.info("Successfully uploaded ZIP file for user: {} - {} entries, {} attachments",
                    userId, savedEntries.size(), filenameToChecksum.size());

            return resultBuilder.build();
        } catch (Exception e) {
            // Create detailed error information
            UploadError error = createUploadError(e, "ZIP file processing");
            String errorMsg = error.getUserMessage();

            log.error(
                    "ZIP file upload failed for user: {} - Error Code: {}, Message: {}, Technical: {}",
                    userId, error.getErrorCode(), errorMsg, error.getTechnicalDetails(), e);

            return resultBuilder.errorMessage(errorMsg).build();
        } finally {
            // Clean up temporary file
            try {
                Files.deleteIfExists(filePath);
                log.info("Temporary file cleaned up: {}", filePath);
            } catch (IOException e) {
                log.warn("Failed to clean up temporary file: {} - {}", filePath, e.getMessage());
            }
        }
    }

    /**
     * Upload and process a ZIP file containing chat data and multimedia files
     */
    @Transactional
    public UploadResult uploadZipFile(MultipartFile file, Long userId) {
        log.info("Starting ZIP file upload for user: {} with file: {}", userId,
                file.getOriginalFilename());

        UploadResult.UploadResultBuilder resultBuilder = UploadResult.builder()
                .originalFileName(file.getOriginalFilename()).fileType("zip").success(false);

        try {
            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                String errorMsg =
                        "File size exceeds maximum allowed size of " + MAX_FILE_SIZE + " bytes";
                log.warn("File size validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Validate zip file name
            if (!StringUtils.hasText(file.getOriginalFilename())) {
                String errorMsg = "No file name was supplied";
                log.warn("File name validation failed for user: {} - {}", userId, errorMsg);
                return resultBuilder.errorMessage(errorMsg).build();
            }

            // Generate unique chat ID
            String chatId = generateChatId(file.getOriginalFilename(), userId);
            resultBuilder.chatId(chatId);

            Set<ChatEntry> chatEntries = new LinkedHashSet<>(512);
            Map<String, String> filenameToChecksum = new HashMap<>();
            List<String> extractedFiles = new ArrayList<>();

            try (ZipInputStream zis =
                    new ZipInputStream(new BufferedInputStream(file.getInputStream(), 80 * 1024))) {
                ZipEntry entry;
                int entryCount = 0;

                while ((entry = zis.getNextEntry()) != null && entryCount < MAX_ENTRIES_PER_ZIP) {
                    String fileName = entry.getName();
                    extractedFiles.add(fileName);

                    try {
                        if (isChatTextFile(fileName)) {
                            // Process text file (chat data)
                            processChatTextStream(zis, chatEntries);
                            entryCount++;
                        } else {
                            // Process multimedia file
                            String contentHash = processMultimediaFile(zis, fileName, userId);
                            if (contentHash != null) {
                                filenameToChecksum.put(fileName, contentHash);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing ZIP entry: {} - {}", fileName, e.getMessage());
                        // Continue processing other entries even if one fails
                    } finally {
                        // Ensure the current entry is closed and ready for the next one
                        try {
                            zis.closeEntry();
                        } catch (IOException e) {
                            log.warn("Error closing ZIP entry: {} - {}", fileName, e.getMessage());
                        }
                    }
                }
            }
            // Remove any duplicate entries that might have been parsed
            log.info("Processed ZIP file for user: {} - {} chat entries, removing duplicates...",
                    userId, chatEntries.size());
            List<ChatEntry> deduplicatedEntries = deduplicateEntries(new ArrayList<>(chatEntries));
            log.info("After deduplication: {} unique entries", deduplicatedEntries.size());

            log.info("Processed ZIP file for user: {} - {} chat entries, {} attachments", userId,
                    deduplicatedEntries.size(), filenameToChecksum.size());

            // Save to database
            log.info("Starting database save for user: {} and chat: {}", userId, chatId);
            List<ChatEntryEntity> savedEntries = performIncrementalUpdate(
                    new LinkedHashSet<>(deduplicatedEntries), userId, chatId, filenameToChecksum);

            resultBuilder.totalEntries(savedEntries.size())
                    .totalAttachments(filenameToChecksum.size()).extractedFiles(extractedFiles)
                    .success(true);
            log.info("Successfully uploaded ZIP file for user: {} - {} entries, {} attachments",
                    userId, savedEntries.size(), filenameToChecksum.size());

            return resultBuilder.build();
        } catch (Exception e) {
            // Create detailed error information
            UploadError error = createUploadError(e, "ZIP file processing");
            String errorMsg = error.getUserMessage();

            log.error(
                    "ZIP file upload failed for user: {} - Error Code: {}, Message: {}, Technical: {}",
                    userId, error.getErrorCode(), errorMsg, error.getTechnicalDetails(), e);

            return resultBuilder.errorMessage(errorMsg).build();
        }
    }

    /**
     * Process chat text stream and extract chat entries
     */
    private void processChatTextStream(InputStream chatTextStream, Set<ChatEntry> chatEntries)
            throws IOException {
        // Use BufferedReader but don't let it close the underlying stream
        BufferedReader reader = new BufferedReader(new InputStreamReader(chatTextStream) {
            @Override
            public void close() {
                // Don't close the underlying stream
                // super.close(); // Commented out to prevent closing ZipInputStream
            }
        });

        try {
            String line;
            StringBuilder currentEntry = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (isNewEntry(line)) {
                    // Process the previous entry if it exists
                    if (!currentEntry.isEmpty()) {
                        parseChatEntry(currentEntry.toString()).ifPresent(chatEntries::add);
                    }
                    // Start new entry
                    currentEntry = new StringBuilder(line);
                } else {
                    // Continue the current entry
                    currentEntry.append("\n").append(line);
                }
            }

            // Process the last entry
            if (!currentEntry.isEmpty()) {
                parseChatEntry(currentEntry.toString()).ifPresent(chatEntries::add);
            }
        } finally {
            // Don't close the reader as it would close the underlying stream
            // reader.close(); // Commented out to prevent closing ZipInputStream
        }
    }

    /**
     * Process multimedia file from ZIP stream
     */
    private String processMultimediaFile(ZipInputStream zis, String fileName, Long userId) {
        log.debug("Processing multimedia file: {} for user: {}", fileName, userId);

        Path tempFile = null;
        try {
            // Use larger buffer for better I/O performance
            byte[] buffer = new byte[8192];
            int bytesRead;
            int totalBytes = 0;

            // Stream-based hash calculation to avoid loading entire file into memory
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Start with ByteArrayOutputStream, switch to temp file if needed
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean usingTempFile = false;
            final int MEMORY_THRESHOLD = 100 * 1024; // 100KB threshold

            // Read file content and calculate hash in one pass
            while ((bytesRead = zis.read(buffer)) != -1) {
                // Check file size limit early
                totalBytes += bytesRead;
                if (totalBytes > MAX_FILE_SIZE) {
                    log.warn("Multimedia file too large: {} ({} bytes) for user: {}", fileName,
                            totalBytes, userId);
                    return null;
                }

                // Update hash calculation
                digest.update(buffer, 0, bytesRead);

                // Handle memory vs file storage
                if (!usingTempFile && baos.size() + bytesRead > MEMORY_THRESHOLD) {
                    // Switch to temp file
                    try {
                        tempFile = Files.createTempFile("whatsapp_upload_", ".tmp");
                        log.debug("Switching to temp file for large multimedia: {} -> {}", fileName,
                                tempFile);

                        // Write existing data to temp file
                        Files.write(tempFile, baos.toByteArray());
                        baos = null; // Release memory
                        usingTempFile = true;

                        // Append current buffer to temp file
                        byte[] bufferToWrite = new byte[bytesRead];
                        System.arraycopy(buffer, 0, bufferToWrite, 0, bytesRead);
                        Files.write(tempFile, bufferToWrite,
                                java.nio.file.StandardOpenOption.APPEND);

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
                log.debug("File already exists, skipping save: {} (hash: {})", fileName,
                        contentHash);
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
                        log.error("Failed to move temp file to final location: {} -> {}", tempFile,
                                finalFilePath, e);
                        throw new RuntimeException("Failed to move temp file", e);
                    }
                } else {
                    // Save from memory
                    saveFileToSystem(finalFilePath, baos.toByteArray(), fileName, totalBytes);
                }
            }
            // Save attachment information to database (idempotent operation)
            saveAttachmentToDatabase(contentHash, totalBytes, fileName, userId);
            return contentHash;
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
    private void saveFileToSystem(Path filePath, byte[] fileContent, String fileName,
            int totalBytes) {
        try {
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("Created directory: {}", parentDir);
            }

            // Write the file content atomically
            Files.write(filePath, fileContent);
            log.info("Saved multimedia file: {} to path: {} (size: {} bytes)", fileName, filePath,
                    totalBytes);

        } catch (Exception e) {
            log.error("Failed to save multimedia file to filesystem: {} - {}", fileName,
                    e.getMessage());
            // Don't throw exception, continue processing
        }
    }

    /**
     * Save attachment information to database with proper error handling
     */
    private void saveAttachmentToDatabase(String contentHash, int totalBytes, String fileName,
            Long userId) {
        try {
            attachmentService.saveAttachment(contentHash, (long) totalBytes);
            log.debug("Saved attachment: {} with hash: {} and size: {} bytes for user: {}",
                    fileName, contentHash, totalBytes, userId);
        } catch (Exception e) {
            log.error("Failed to save attachment to database: {} - {}", fileName, e.getMessage());
            // Don't throw exception, continue processing
        }
    }

    /**
     * Perform incremental update of chat entries, removing entries that are no longer present and
     * adding new ones
     */
    private List<ChatEntryEntity> performIncrementalUpdate(Set<ChatEntry> newEntries, Long userId,
            String chatId, Map<String, String> filenameToHashMap) {
        log.info("Performing incremental update for {} entries, user: {}, chat: {}",
                newEntries.size(), userId, chatId);

        // Check if this chat already exists
        boolean chatExists = chatService.chatExists(userId, chatId);

        if (chatExists) {
            log.info("Chat already exists for user: {} and chat: {}, performing incremental update",
                    userId, chatId);
            return performIncrementalUpdateForExistingChat(newEntries, userId, chatId,
                    filenameToHashMap);
        } else {
            log.info("New chat for user: {} and chat: {}, performing bulk insert", userId, chatId);
            try {
                return performBulkInsertForNewChat(newEntries, userId, chatId, filenameToHashMap);
            } catch (Exception e) {
                log.error(
                        "Bulk insert failed in performIncrementalUpdate for user: {} and chat: {} - {}",
                        userId, chatId, e.getMessage());
                throw e; // Re-throw to ensure error is propagated
            }
        }
    }

    /**
     * Perform incremental update for existing chat - remove obsolete entries and process one by one
     */
    private List<ChatEntryEntity> performIncrementalUpdateForExistingChat(Set<ChatEntry> newEntries,
            Long userId, String chatId, Map<String, String> filenameToHashMap) {
        // First, remove entries that are no longer present in the new zip
        removeObsoleteEntries(newEntries, userId, chatId);

        List<ChatEntryEntity> savedEntries = new ArrayList<>();
        int skippedDuplicates = 0;
        int constraintViolations = 0;

        // Process entries one by one to handle duplicates gracefully
        for (ChatEntry entry : newEntries) {
            try {
                // Create the entity key for duplicate checking
                String entryKey = createEntryKey(entry);

                // Check if this entry already exists in the database
                boolean exists = chatEntryService.existsByUniqueFields(userId, chatId,
                        entry.getLocalDateTime(), entry.getAuthor(), entry.getFileName());

                if (exists) {
                    log.debug("Skipping duplicate entry: user={}, chat={}, time={}, author={}",
                            userId, chatId, entry.getLocalDateTime(), entry.getAuthor());
                    skippedDuplicates++;
                    continue;
                }

                // Create new entity
                ChatEntryEntity entity = ChatEntryEntity.fromChatEntry(entry, userId, chatId);

                // Link attachment if this entry has a filename that matches our hash map
                if (entry.getFileName() != null
                        && filenameToHashMap.containsKey(entry.getFileName())) {
                    String hash = filenameToHashMap.get(entry.getFileName());

                    // Find the attachment by hash
                    attachmentService.findByHash(hash).ifPresent(attachment -> {
                        entity.setAttachment(attachment);
                        entity.setPath(attachmentService.generateFilePath(hash));
                    });
                }

                // Save individual entity with improved error handling
                try {
                    ChatEntryEntity savedEntity = chatEntryService.saveChatEntry(entity);
                    savedEntries.add(savedEntity);
                } catch (Exception saveException) {
                    // Handle constraint violation gracefully with better error detection
                    String errorMessage = saveException.getMessage();
                    if (errorMessage != null) {
                        if (errorMessage.contains("duplicate key value violates unique constraint")
                                || errorMessage.contains("idx_chat_entries_unique_entry")) {
                            log.debug(
                                    "Entry already exists (constraint violation), skipping: {} - {}",
                                    entryKey, errorMessage);
                            constraintViolations++;
                        } else if (errorMessage.contains("duplicate key")) {
                            log.debug("Entry already exists (duplicate key), skipping: {} - {}",
                                    entryKey, errorMessage);
                            constraintViolations++;
                        } else {
                            log.error("Failed to save chat entry: {} - {}", entry, errorMessage);
                        }
                    } else {
                        log.error("Failed to save chat entry: {} - Unknown error", entry,
                                saveException);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to process chat entry: {} - {}", entry, e.getMessage(), e);
                // Continue processing other entries
            }
        }

        log.info(
                "Incremental update completed for user: {}, chat: {} - Saved: {}, Skipped duplicates: {}, Constraint violations: {}",
                userId, chatId, savedEntries.size(), skippedDuplicates, constraintViolations);
        return savedEntries;
    }

    /**
     * Perform bulk insert for new chat - now with duplicate checking to prevent constraint
     * violations
     */
    private List<ChatEntryEntity> performBulkInsertForNewChat(Set<ChatEntry> newEntries,
            Long userId, String chatId, Map<String, String> filenameToHashMap) {
        List<ChatEntry> entriesToSave = new ArrayList<>();
        int skippedDuplicates = 0;

        // Prepare all entries for bulk insert with duplicate checking
        for (ChatEntry entry : newEntries) {
            // Check if this entry already exists in the database
            boolean exists = chatEntryService.existsByUniqueFields(userId, chatId,
                    entry.getLocalDateTime(), entry.getAuthor(), entry.getFileName());

            if (exists) {
                log.debug(
                        "Skipping duplicate entry during bulk insert: user={}, chat={}, time={}, author={}",
                        userId, chatId, entry.getLocalDateTime(), entry.getAuthor());
                skippedDuplicates++;
                continue;
            }

            // Convert to ChatEntry for bulk save (since saveChatEntries expects ChatEntry objects)
            ChatEntry entryToSave = ChatEntry.builder().localDateTime(entry.getLocalDateTime())
                    .author(entry.getAuthor()).payload(entry.getPayload())
                    .fileName(entry.getFileName()).type(entry.getType()).build();

            entriesToSave.add(entryToSave);
        }

        // Perform bulk insert
        List<ChatEntryEntity> savedEntries;
        try {
            savedEntries = chatEntryService.saveChatEntries(entriesToSave, userId, chatId);
        } catch (Exception e) {
            log.error("Bulk insert failed for user: {} and chat: {} - {}", userId, chatId,
                    e.getMessage());
            throw e; // Re-throw to ensure error is propagated
        }

        log.info(
                "Bulk insert completed for new chat - user: {}, chat: {}, saved: {} entries, skipped: {} duplicates",
                userId, chatId, savedEntries.size(), skippedDuplicates);

        return savedEntries;
    }

    /**
     * Remove chat entries that are no longer present in the new zip file
     */
    private void removeObsoleteEntries(Set<ChatEntry> newEntries, Long userId, String chatId) {
        try {
            // Get all existing entries for this chat from the database
            List<ChatEntryEntity> existingEntries =
                    chatEntryService.findByUserIdAndChatId(userId, chatId);

            if (existingEntries.isEmpty()) {
                log.debug("No existing entries found for user: {} and chat: {}, skipping cleanup",
                        userId, chatId);
                return;
            }

            log.info(
                    "Found {} existing entries for user: {} and chat: {}, checking for obsolete entries",
                    existingEntries.size(), userId, chatId);

            int removedCount = 0;
            List<Long> entriesToRemove = new ArrayList<>();

            // Check each existing entry to see if it's still present in the new zip
            for (ChatEntryEntity existingEntry : existingEntries) {
                boolean stillExists = newEntries.stream()
                        .anyMatch(newEntry -> isSameEntry(existingEntry, newEntry));

                if (!stillExists) {
                    entriesToRemove.add(existingEntry.getId());
                    removedCount++;
                }
            }

            // Remove obsolete entries
            if (!entriesToRemove.isEmpty()) {
                log.info("Removing {} obsolete entries for user: {} and chat: {}", removedCount,
                        userId, chatId);

                for (Long entryId : entriesToRemove) {
                    chatEntryService.deleteById(entryId, userId);
                }

                log.info("Successfully removed {} obsolete entries for user: {} and chat: {}",
                        removedCount, userId, chatId);
            } else {
                log.info("No obsolete entries found for user: {} and chat: {}", userId, chatId);
            }

        } catch (Exception e) {
            log.error("Error while removing obsolete entries for user: {} and chat: {} - {}",
                    userId, chatId, e.getMessage(), e);
            // Don't fail the entire upload if cleanup fails
        }
    }

    /**
     * Check if two chat entries represent the same message
     */
    private boolean isSameEntry(ChatEntryEntity existingEntry, ChatEntry newEntry) {
        // Compare the key fields that identify a unique chat entry
        return existingEntry.getLocalDateTime().equals(newEntry.getLocalDateTime())
                && existingEntry.getAuthor().equals(newEntry.getAuthor())
                && Objects.equals(existingEntry.getPayload(), newEntry.getPayload())
                && Objects.equals(existingEntry.getFileName(), newEntry.getFileName());
    }

    private boolean isNewEntry(String line) {
        return TIMESTAMP_PATTERN.matcher(line).lookingAt();
    }

    private boolean isChatTextFile(String fileName) {
        return fileName.toLowerCase().startsWith("whatsapp chat")
                && fileName.toLowerCase().endsWith(".txt");
    }

    private Optional<ChatEntry> parseChatEntry(String entryText) {
        ChatEntry.ChatEntryBuilder builder = ChatEntry.builder();

        // Parse timestamp
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(entryText);
        if (timestampMatcher.find()) {
            // Parse the timestamp to LocalDateTime
            String timestamp = timestampMatcher.group(1);
            try {
                LocalDateTime localDateTime = parseTimestamp(timestamp);
                builder.localDateTime(localDateTime);
            } catch (DateTimeParseException e) {
                log.warn("Failed to parse timestamp: {}", timestamp);
            }

            // Split remaining text
            String[] parts = entryText.substring(timestampMatcher.end()).split(": ", 2);
            if (parts.length == 2) {
                builder.author(parts[0].trim());
                String payload = parts[1].trim();

                // Check for file attachment
                String[] maybeWithAttachment = payload.split("\\s\\(file attached\\)", 2);
                switch (maybeWithAttachment.length) {
                    case 1 -> // No file attachment
                            builder.payload(payload);
                    case 2 -> {
                        // File attachment found
                        if (StringUtils.hasText(maybeWithAttachment[0])) {
                            String fileName = maybeWithAttachment[0];
                            builder.fileName(fileName);
                        }
                        if (StringUtils.hasText(maybeWithAttachment[1])) {
                            // Remove leading CR/LF
                            payload = maybeWithAttachment[1].substring(1); // Remove leading CR/LF
                            builder.payload(payload);
                        }
                    }
                }

                // Determine message type
                ChatEntry.Type type = determineMessageType(maybeWithAttachment.length > 1,
                        maybeWithAttachment.length > 1 ? maybeWithAttachment[0] : payload);
                builder.type(type);
                return Optional.of(builder.build());
            }
        }
        // The line does not start with a valid timestamp. It magit be continuation of a previous
        // entry
        return Optional.empty();
    }

    /**
     * Parse timestamp string to LocalDateTime
     */
    private LocalDateTime parseTimestamp(String timestampString) {
        return LocalDateTime.parse(timestampString, DATE_TIME_FORMATTER);
    }

    /**
     * Determine message type based on content and attachment presence
     */
    private ChatEntry.Type determineMessageType(boolean hasAttachment, String content) {
        if (!hasAttachment) {
            return ChatEntry.Type.TEXT;
        }

        String lowerContent = content.toLowerCase();
        if (lowerContent.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
            return ChatEntry.Type.IMAGE;
        } else if (lowerContent.matches(".*\\.(mp4|avi|mov|wmv|flv|webm|mkv)$")) {
            return ChatEntry.Type.VIDEO;
        } else if (lowerContent.matches(".*\\.(mp3|wav|ogg|m4a|aac|opus)$")) {
            return ChatEntry.Type.AUDIO;
        } else if (lowerContent.matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|rtf)$")) {
            return ChatEntry.Type.DOCUMENT;
        } else {
            return ChatEntry.Type.FILE;
        }
    }

    /**
     * Create a unique key for a chat entry based on its content This is used to identify duplicate
     * entries across uploads
     */
    private String createEntryKey(ChatEntry entry) {
        return String.format("%s_%s_%s_%s", entry.getLocalDateTime(), entry.getAuthor(),
                entry.getPayload() != null ? entry.getPayload() : "",
                entry.getFileName() != null ? entry.getFileName() : "");
    }

    /**
     * Create a unique key for a chat entry entity based on its content
     */
    private String createEntryKey(ChatEntryEntity entry) {
        return String.format("%s_%s_%s_%s", entry.getLocalDateTime(), entry.getAuthor(),
                entry.getPayload() != null ? entry.getPayload() : "",
                entry.getFileName() != null ? entry.getFileName() : "");
    }

    /**
     * Remove duplicate entries based on content
     */
    private List<ChatEntry> deduplicateEntries(List<ChatEntry> entries) {
        return new ArrayList<>(new LinkedHashSet<>(entries));
    }

    @Getter
    public static class UploadResult {
        private String chatId;
        private String originalFileName;
        private String fileType;
        private int totalEntries;
        private int totalAttachments;
        private List<String> extractedFiles;
        private boolean success;
        private String errorMessage;

        public static UploadResultBuilder builder() {
            return new UploadResultBuilder();
        }

        public static class UploadResultBuilder {
            private final UploadResult result = new UploadResult();

            public UploadResultBuilder chatId(String chatId) {
                result.chatId = chatId;
                return this;
            }

            public UploadResultBuilder originalFileName(String originalFileName) {
                result.originalFileName = originalFileName;
                return this;
            }

            public UploadResultBuilder fileType(String fileType) {
                result.fileType = fileType;
                return this;
            }

            public UploadResultBuilder totalEntries(int totalEntries) {
                result.totalEntries = totalEntries;
                return this;
            }

            public UploadResultBuilder totalAttachments(int totalAttachments) {
                result.totalAttachments = totalAttachments;
                return this;
            }

            public UploadResultBuilder extractedFiles(List<String> extractedFiles) {
                result.extractedFiles = extractedFiles;
                return this;
            }

            public UploadResultBuilder success(boolean success) {
                result.success = success;
                return this;
            }

            public UploadResultBuilder errorMessage(String errorMessage) {
                result.errorMessage = errorMessage;
                return this;
            }

            public UploadResult build() {
                return result;
            }
        }
    }

    /**
     * Progress tracking for async uploads
     */
    @Getter
    public static class UploadProgress {
        private final String uploadId;
        private final Long userId;
        private final MultipartFile file;
        private final Path tempFile;
        private int progress;
        private String message;
        private UploadResult result;
        private String error;

        public UploadProgress(String uploadId, Long userId, MultipartFile file, Path tempFile) {
            this.uploadId = uploadId;
            this.userId = userId;
            this.file = file;
            this.tempFile = tempFile;
            this.progress = 0;
            this.message = "Starting upload...";
        }

        public void updateProgress(int progress, String message) {
            this.progress = progress;
            this.message = message;
        }

        public void setResult(UploadResult result) {
            this.result = result;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Start async ZIP processing
     */
    public String startAsyncZipProcessing(MultipartFile file, Long userId) {
        String uploadId = UUID.randomUUID().toString();
        log.info("Starting async ZIP processing for user: {} with upload ID: {}", userId, uploadId);

        // Create a temporary file in our application's temp directory
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload_" + uploadId + "_", ".zip");
            file.transferTo(tempFile.toFile());
            log.info("File saved to temporary location for upload: {} - Path: {}", uploadId,
                    tempFile);
        } catch (IOException e) {
            log.error("Failed to save file to temporary location for upload: {} - {}", uploadId,
                    e.getMessage());
            throw new RuntimeException("Failed to save uploaded file", e);
        }

        UploadProgress progress = new UploadProgress(uploadId, userId, file, tempFile);
        uploadProgress.put(uploadId, progress);

        // Start processing in background thread
        new Thread(() -> {
            try {
                processZipFileAsync(uploadId, progress, userId);
            } catch (Exception e) {
                log.error("Async ZIP processing failed for upload: {}", uploadId, e);
                progress.setError(e.getMessage());

                // Send detailed error information to client
                UploadError error = createUploadError(e, "ZIP processing failed");
                sendProgressUpdate(uploadId, "error", error);
            }
        }).start();

        return uploadId;
    }

    /**
     * Wait for client to establish SSE connection
     */
    private void waitForClientConnection(String uploadId, int timeoutMs) {
        log.info("Starting connection wait for upload: {} - timeout: {}ms", uploadId, timeoutMs);

        // Wait for the SSE emitter to be created and the client to connect
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            SseEmitter emitter = progressEmitters.get(uploadId);
            if (emitter != null) {
                log.info("SSE emitter found for upload: {}, waiting for client connection...",
                        uploadId);

                // Wait for client to actually connect and receive the ready message
                // Since SSE is one-way, we wait a reasonable time for the client to establish
                // connection
                try {
                    Thread.sleep(2000); // Wait 2 seconds for client to establish connection
                    log.info("Client SSE connection wait completed for upload: {}", uploadId);
                    return;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Connection wait interrupted for upload: {}", uploadId);
                    return;
                }
            }
            try {
                Thread.sleep(200); // Check every 200ms
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Connection wait interrupted for upload: {}", uploadId);
                return;
            }
        }
        log.warn("Timeout waiting for SSE emitter for upload: {}", uploadId);
    }

    /**
     * Get upload progress status (fallback for when SSE fails)
     */
    public UploadProgress getUploadProgress(String uploadId) {
        return uploadProgress.get(uploadId);
    }

    /**
     * Create progress emitter for monitoring
     */
    public SseEmitter createProgressEmitter(String uploadId) {
        log.info("Creating progress emitter for upload: {}", uploadId);
        SseEmitter emitter = new SseEmitter(300000L); // 5 minutes timeout

        progressEmitters.put(uploadId, emitter);
        log.info("SSE emitter stored for upload: {} - Total emitters: {}", uploadId,
                progressEmitters.size());

        emitter.onCompletion(() -> {
            log.info("SSE emitter completed for upload: {}", uploadId);
            progressEmitters.remove(uploadId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE emitter timed out for upload: {}", uploadId);
            progressEmitters.remove(uploadId);
        });
        emitter.onError((ex) -> {
            log.error("SSE emitter error for upload: {} - {}", uploadId, ex.getMessage());
            progressEmitters.remove(uploadId);
        });

        // Send a connection ready event to indicate the server is ready
        try {
            Map<String, Object> readyMessage = new HashMap<>();
            readyMessage.put("type", "ready");
            readyMessage.put("message", "Server ready to send events");
            emitter.send(readyMessage);
            log.info("Connection ready message sent for upload: {}", uploadId);

            // Wait a bit for the client to receive and process the ready message
            try {
                Thread.sleep(500); // Wait 500ms for client to process ready message
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Connection ready wait interrupted for upload: {}", uploadId);
            }
        } catch (Exception e) {
            log.error("Failed to send connection ready message for upload: {} - {}", uploadId,
                    e.getMessage());
        }

        return emitter;
    }

    /**
     * Process ZIP file asynchronously with progress updates
     */
    private void processZipFileAsync(String uploadId, UploadProgress progress, Long userId) {
        if (progress == null)
            return;

        try {
            // Wait for client to establish SSE connection before sending any events
            log.info("Waiting for client SSE connection for upload: {}", uploadId);
            waitForClientConnection(uploadId, 10000); // Wait up to 10 seconds

            // Update progress: Starting
            progress.updateProgress(0, "Starting ZIP processing...");
            sendProgressUpdate(uploadId, "progress", 0, "Starting ZIP processing...");

            // Update progress: Reading ZIP
            progress.updateProgress(20, "Reading ZIP file...");
            sendProgressUpdate(uploadId, "progress", 20, "Reading ZIP file...");

            // Process the ZIP file using the stored temporary file
            UploadResult result = uploadZipFileFromPath(progress.getTempFile(),
                    progress.getFile().getOriginalFilename(), userId);
            log.info("ZIP processing result for upload: {} - Success: {}, Entries: {}, Error: {}",
                    uploadId, result.isSuccess(), result.getTotalEntries(),
                    result.getErrorMessage());

            // Check if the result indicates an error
            if (!result.isSuccess()) {
                log.warn("ZIP processing returned unsuccessful result for upload: {} - Error: {}",
                        uploadId, result.getErrorMessage());

                // Debug: Check if SSE emitter exists
                log.info("SSE emitter check for upload: {} - Emitter exists: {}", uploadId,
                        progressEmitters.containsKey(uploadId));

                // Create error object from the result
                UploadError error = UploadError.builder().errorCode("UPLOAD_FAILED")
                        .userMessage(result.getErrorMessage() != null ? result.getErrorMessage()
                                : "Upload failed")
                        .technicalDetails("Server returned unsuccessful result")
                        .context("ZIP file processing").timestamp(LocalDateTime.now()).build();

                log.info(
                        "Attempting to send error notification for unsuccessful result - upload: {}",
                        uploadId);

                // Check if SSE emitter is still available before sending error
                SseEmitter emitter = progressEmitters.get(uploadId);
                if (emitter != null) {
                    sendProgressUpdate(uploadId, "error", error);
                    log.info("Error notification sent for unsuccessful result - upload: {}",
                            uploadId);
                } else {
                    log.warn("SSE emitter not available for error notification - upload: {}",
                            uploadId);
                    // Store error in progress for potential fallback retrieval
                    progress.setError(error.getUserMessage());
                }
                return;
            }

            // Update progress: Complete
            progress.updateProgress(100, "Processing complete!");
            progress.setResult(result);
            sendProgressUpdate(uploadId, "complete", result);

        } catch (Exception e) {
            log.error("ZIP processing failed for upload: {} - {}", uploadId, e.getMessage(), e);
            progress.setError(e.getMessage());

            // Send detailed error information to client
            UploadError error = createUploadError(e, "ZIP processing failed");
            log.info("Created error object for upload: {} - Code: {}, Message: {}", uploadId,
                    error.getErrorCode(), error.getUserMessage());

            try {
                log.info("Attempting to send error notification to client for upload: {}",
                        uploadId);
                sendProgressUpdate(uploadId, "error", error);
                log.info("Error notification sent to client for upload: {}", uploadId);

                // Wait a bit longer to ensure the error event is fully processed
                try {
                    Thread.sleep(500); // 500ms delay to ensure error delivery
                    log.info("Error notification delay completed for upload: {}", uploadId);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Error notification delay interrupted for upload: {}", uploadId);
                }
            } catch (Exception notificationException) {
                log.error("Failed to send error notification to client for upload: {} - {}",
                        uploadId, notificationException.getMessage(), notificationException);
            }
        }
    }

    /**
     * Create a structured upload error from an exception or error
     */
    private UploadError createUploadError(Throwable e, String context) {
        String errorCode = "UPLOAD_ERROR";
        String userMessage = "An error occurred during upload processing";
        String technicalDetails = e.getMessage();

        // Determine specific error type and provide user-friendly messages
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            errorCode = "DATABASE_CONSTRAINT_ERROR";
            userMessage =
                    "Upload failed due to data integrity issues. This usually means some entries already exist.";
            technicalDetails = "Database constraint violation: " + e.getMessage();
        } else if (e instanceof java.io.IOException) {
            errorCode = "FILE_IO_ERROR";
            userMessage =
                    "Upload failed due to file reading issues. Please check if the file is corrupted.";
            technicalDetails = "File I/O error: " + e.getMessage();
        } else if (e instanceof java.util.zip.ZipException) {
            errorCode = "ZIP_CORRUPTION_ERROR";
            userMessage = "Upload failed because the ZIP file appears to be corrupted or invalid.";
            technicalDetails = "ZIP file error: " + e.getMessage();
        } else if (e instanceof org.springframework.transaction.UnexpectedRollbackException) {
            errorCode = "TRANSACTION_ERROR";
            userMessage = "Upload failed due to database transaction issues. Please try again.";
            technicalDetails = "Transaction rollback: " + e.getMessage();
        } else if (e instanceof java.lang.OutOfMemoryError
                || e instanceof java.lang.VirtualMachineError) {
            errorCode = "MEMORY_ERROR";
            userMessage =
                    "Upload failed due to insufficient server memory. Try uploading a smaller file.";
            technicalDetails = "Memory error: " + e.getMessage();
        } else if (e instanceof java.lang.IllegalArgumentException) {
            errorCode = "VALIDATION_ERROR";
            userMessage =
                    "Upload failed due to invalid file format or content. Please check your file.";
            technicalDetails = "Validation error: " + e.getMessage();
        } else if (e instanceof java.lang.SecurityException) {
            errorCode = "SECURITY_ERROR";
            userMessage = "Upload failed due to security restrictions. Please contact support.";
            technicalDetails = "Security error: " + e.getMessage();
        } else if (e instanceof java.util.concurrent.TimeoutException) {
            errorCode = "TIMEOUT_ERROR";
            userMessage =
                    "Upload failed due to timeout. The file may be too large or the server is busy.";
            technicalDetails = "Timeout error: " + e.getMessage();
        }

        return UploadError.builder().errorCode(errorCode).userMessage(userMessage)
                .technicalDetails(technicalDetails).context(context).timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Structured upload error information
     */
    @Getter
    @Builder
    public static class UploadError {
        private final String errorCode;
        private final String userMessage;
        private final String technicalDetails;
        private final String context;
        private final LocalDateTime timestamp;
    }

    /**
     * Send progress update to client
     */
    private void sendProgressUpdate(String uploadId, String type, Object... data) {
        log.info("Attempting to send progress update for upload: {}, type: {}", uploadId, type);

        SseEmitter emitter = progressEmitters.get(uploadId);
        log.info("SSE emitter lookup for upload: {} - Found: {}, Type: {}", uploadId,
                emitter != null, type);

        if (emitter == null) {
            log.warn("No SSE emitter found for upload: {}", uploadId);
            log.warn("Available upload IDs: {}", progressEmitters.keySet());
            return;
        }

        try {
            // Check if the emitter is still active (SseEmitter doesn't have isCompleted method)
            // We'll just try to send and handle any exceptions

            Map<String, Object> message = new HashMap<>();
            message.put("type", type);

            if (type.equals("progress")) {
                message.put("progress", data[0]);
                message.put("message", data[1]);
            } else if (type.equals("complete")) {
                message.put("result", data[0]);
            } else if (type.equals("error")) {
                // Handle structured error information
                if (data[0] instanceof UploadError) {
                    UploadError error = (UploadError) data[0];
                    message.put("errorCode", error.getErrorCode());
                    message.put("userMessage", error.getUserMessage());
                    message.put("technicalDetails", error.getTechnicalDetails());
                    message.put("context", error.getContext());
                    message.put("timestamp", error.getTimestamp());
                } else {
                    // Fallback for simple string errors
                    message.put("message", data[0]);
                }
            }

            emitter.send(message);
            log.info("Successfully sent {} event for upload: {}", type, uploadId);

            if (type.equals("complete")) {
                // For complete events, close immediately
                emitter.complete();
                progressEmitters.remove(uploadId);
                uploadProgress.remove(uploadId);
                log.debug("SSE emitter completed for upload: {}", uploadId);
            } else if (type.equals("error")) {
                // For error events, add a small delay before closing to ensure delivery
                try {
                    Thread.sleep(200); // 200ms delay for error events
                    emitter.complete();
                    progressEmitters.remove(uploadId);
                    uploadProgress.remove(uploadId);
                    log.debug("SSE emitter completed after error for upload: {}", uploadId);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    emitter.complete();
                    progressEmitters.remove(uploadId);
                    uploadProgress.remove(uploadId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send progress update for upload: {}", uploadId, e);
            progressEmitters.remove(uploadId);
        }
    }
}
