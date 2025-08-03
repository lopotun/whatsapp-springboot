package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Attachment;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
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
        if (originalFileName.contains(".")) {
            baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        }

        // Remove any non-alphanumeric characters and replace with underscores
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "_");

        // Add timestamp to ensure uniqueness
        String timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Add user ID to prevent conflicts between users
        return String.format("user%d_%s_%s", userId, baseName, timestamp);
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

            // Generate unique chat ID
            String chatId = generateChatId(file.getOriginalFilename(), userId);
            resultBuilder.chatId(chatId);

            // Parse chat entries from the text file
            List<ChatEntry> chatEntries = new ArrayList<>();
            Map<String, String> filenameToHashMap = new HashMap<>();

            try (InputStream inputStream = file.getInputStream();
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                StringBuilder currentEntry = new StringBuilder();
                int entryCount = 0;

                while ((line = reader.readLine()) != null && entryCount < MAX_ENTRIES) {
                    if (isNewEntry(line)) {
                        // Process the previous entry if it exists
                        if (currentEntry.length() > 0) {
                            ChatEntry entry = parseChatEntry(currentEntry.toString());
                            if (entry != null) {
                                chatEntries.add(entry);
                                entryCount++;
                            }
                        }
                        // Start new entry
                        currentEntry = new StringBuilder(line);
                    } else {
                        // Continue the current entry
                        currentEntry.append("\n").append(line);
                    }
                }

                // Process the last entry
                if (currentEntry.length() > 0 && entryCount < MAX_ENTRIES) {
                    ChatEntry entry = parseChatEntry(currentEntry.toString());
                    if (entry != null) {
                        chatEntries.add(entry);
                    }
                }
            }

            log.info("Parsed {} chat entries from text file for user: {}", chatEntries.size(),
                    userId);

            // Remove duplicates and save to database
            List<ChatEntry> uniqueEntries = deduplicateEntries(chatEntries);
            List<ChatEntryEntity> savedEntries =
                    performIncrementalUpdate(uniqueEntries, userId, chatId, filenameToHashMap);

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

            // Generate unique chat ID
            String chatId = generateChatId(file.getOriginalFilename(), userId);
            resultBuilder.chatId(chatId);

            List<ChatEntry> chatEntries = new ArrayList<>();
            Map<String, String> filenameToHashMap = new HashMap<>();
            List<String> extractedFiles = new ArrayList<>();

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                int entryCount = 0;

                while ((entry = zis.getNextEntry()) != null && entryCount < MAX_ENTRIES_PER_ZIP) {
                    String fileName = entry.getName();
                    extractedFiles.add(fileName);

                    try {
                        if (isTextFile(fileName)) {
                            // Process text file (chat data)
                            processChatTextStream(zis, chatEntries, filenameToHashMap);
                            entryCount++;
                        } else {
                            // Process multimedia file
                            String contentHash = processMultimediaFile(zis, fileName, userId);
                            if (contentHash != null) {
                                filenameToHashMap.put(fileName, contentHash);
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

            log.info("Processed ZIP file for user: {} - {} chat entries, {} attachments", userId,
                    chatEntries.size(), filenameToHashMap.size());

            // Remove duplicates and save to database
            List<ChatEntry> uniqueEntries = deduplicateEntries(chatEntries);
            List<ChatEntryEntity> savedEntries =
                    performIncrementalUpdate(uniqueEntries, userId, chatId, filenameToHashMap);

            resultBuilder.totalEntries(savedEntries.size())
                    .totalAttachments(filenameToHashMap.size()).extractedFiles(extractedFiles)
                    .success(true);

            log.info("Successfully uploaded ZIP file for user: {} - {} entries, {} attachments",
                    userId, savedEntries.size(), filenameToHashMap.size());

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
    private void processChatTextStream(InputStream chatTextStream, List<ChatEntry> chatEntries,
            Map<String, String> filenameToHashMap) throws IOException {
        // Use BufferedReader but don't let it close the underlying stream
        BufferedReader reader = new BufferedReader(new InputStreamReader(chatTextStream) {
            @Override
            public void close() throws IOException {
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
                    if (currentEntry.length() > 0) {
                        ChatEntry entry = parseChatEntry(currentEntry.toString());
                        if (entry != null) {
                            chatEntries.add(entry);
                        }
                    }
                    // Start new entry
                    currentEntry = new StringBuilder(line);
                } else {
                    // Continue the current entry
                    currentEntry.append("\n").append(line);
                }
            }

            // Process the last entry
            if (currentEntry.length() > 0) {
                ChatEntry entry = parseChatEntry(currentEntry.toString());
                if (entry != null) {
                    chatEntries.add(entry);
                }
            }
        } finally {
            // Don't close the reader as it would close the underlying stream
            // reader.close(); // Commented out to prevent closing ZipInputStream
        }
    }

    /**
     * Process multimedia file from ZIP stream
     */
    private String processMultimediaFile(ZipInputStream zis, String fileName, Long userId)
            throws IOException {
        log.debug("Processing multimedia file: {} for user: {}", fileName, userId);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalBytes = 0;

            // Read file content - only read the current ZIP entry
            while ((bytesRead = zis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                // Check file size limit
                if (totalBytes > MAX_FILE_SIZE) {
                    log.warn("Multimedia file too large: {} ({} bytes) for user: {}", fileName,
                            totalBytes, userId);
                    return null;
                }
            }

            // Calculate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(baos.toByteArray());
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }

            String contentHash = result.toString();
            byte[] fileContent = baos.toByteArray();

            // Save the file to the multimedia directory
            try {
                Path filePath = fileNamingService.generateFilePathFromHash(contentHash, fileName);

                // Create parent directories if they don't exist
                Path parentDir = filePath.getParent();
                if (!Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                    log.debug("Created directory: {}", parentDir);
                }

                // Write the file content
                Files.write(filePath, fileContent);
                log.info("Saved multimedia file: {} to path: {} (size: {} bytes)", fileName,
                        filePath, totalBytes);

            } catch (Exception e) {
                log.error("Failed to save multimedia file to filesystem: {} - {}", fileName,
                        e.getMessage());
                // Continue processing even if file save fails
            }

            // Save attachment information to database
            try {
                Attachment attachment =
                        attachmentService.saveAttachment(contentHash, (long) totalBytes);
                log.debug("Saved attachment: {} with hash: {} and size: {} bytes for user: {}",
                        fileName, contentHash, totalBytes, userId);
            } catch (Exception e) {
                log.error("Failed to save attachment to database: {} - {}", fileName,
                        e.getMessage());
                // Continue processing even if database save fails
            }

            return contentHash;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            log.error("Error processing multimedia file: {} - {}", fileName, e.getMessage());
            throw new RuntimeException("Error processing multimedia file: " + fileName, e);
        }
    }

    /**
     * Perform incremental update of chat entries, linking attachments
     */
    private List<ChatEntryEntity> performIncrementalUpdate(List<ChatEntry> newEntries, Long userId,
            String chatId, Map<String, String> filenameToHashMap) {
        log.info("Performing incremental update for {} entries, user: {}, chat: {}",
                newEntries.size(), userId, chatId);

        List<ChatEntryEntity> savedEntries = new ArrayList<>();

        for (ChatEntry entry : newEntries) {
            try {
                // Create chat entry entity
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

                // Save the entity
                ChatEntryEntity savedEntity = chatEntryService.saveChatEntry(entity);
                savedEntries.add(savedEntity);

            } catch (Exception e) {
                log.error("Failed to save chat entry: {} - {}", entry, e.getMessage(), e);
                // Continue processing other entries
            }
        }

        log.info("Successfully saved {} chat entries for user: {}, chat: {}", savedEntries.size(),
                userId, chatId);
        return savedEntries;
    }

    private boolean isNewEntry(String line) {
        return TIMESTAMP_PATTERN.matcher(line).lookingAt();
    }

    private boolean isTextFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".text")
                || lowerFileName.endsWith(".log");
    }

    private ChatEntry parseChatEntry(String entryText) {
        ChatEntry.ChatEntryBuilder builder = ChatEntry.builder();

        // Parse timestamp
        java.util.regex.Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(entryText);
        if (timestampMatcher.find()) {
            String timestamp = timestampMatcher.group(1);

            // Parse the timestamp to LocalDateTime
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

                return builder.build();
            }
        }

        return null;
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
        Map<String, ChatEntry> uniqueEntries = new HashMap<>();
        for (ChatEntry entry : entries) {
            String key = createEntryKey(entry);
            if (!uniqueEntries.containsKey(key)) {
                uniqueEntries.put(key, entry);
            }
        }
        return new ArrayList<>(uniqueEntries.values());
    }

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
            private UploadResult result = new UploadResult();

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

        public String getChatId() {
            return chatId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public String getFileType() {
            return fileType;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getTotalAttachments() {
            return totalAttachments;
        }

        public List<String> getExtractedFiles() {
            return extractedFiles;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
