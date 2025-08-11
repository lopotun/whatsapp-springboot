package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
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
            String errorMsg = "Failed to process text file: " + e.getMessage();
            log.error("Text file upload failed for user: {} - {}", userId, errorMsg, e);
            return resultBuilder.errorMessage(errorMsg).build();
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

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
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
            List<ChatEntryEntity> savedEntries = performIncrementalUpdate(
                    new LinkedHashSet<>(deduplicatedEntries), userId, chatId, filenameToChecksum);

            resultBuilder.totalEntries(savedEntries.size())
                    .totalAttachments(filenameToChecksum.size()).extractedFiles(extractedFiles)
                    .success(true);
            log.info("Successfully uploaded ZIP file for user: {} - {} entries, {} attachments",
                    userId, savedEntries.size(), filenameToChecksum.size());

            return resultBuilder.build();
        } catch (Exception e) {
            String errorMsg = "Failed to process ZIP file: " + e.getMessage();
            log.error("ZIP file upload failed for user: {} - {}", userId, errorMsg, e);
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
     * Perform incremental update of chat entries, linking attachments
     */
    private List<ChatEntryEntity> performIncrementalUpdate(Set<ChatEntry> newEntries, Long userId,
            String chatId, Map<String, String> filenameToHashMap) {
        log.info("Performing incremental update for {} entries, user: {}, chat: {}",
                newEntries.size(), userId, chatId);

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
                        entry.getLocalDateTime(), entry.getAuthor(), entry.getPayload(),
                        entry.getFileName());

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
}
