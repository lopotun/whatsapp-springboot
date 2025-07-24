package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEnhancer;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatUploadService {

    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s\\d{1,2}:\\d{2})\\s-\\s");
    private static final long MAX_FILE_SIZE = 5 * 100 * 1024 * 1024; // 500MB limit
    private static final int MAX_ENTRIES = 1000; // Limit number of entries to prevent infinite
                                                 // loops
    private static final int UPLOAD_REQUEST_TIMEOUT = 20 * 60 * 1000; // Limit number of entries per
                                                                      // file to prevent infinite
                                                                      // loops
    private static final int MAX_ENTRIES_PER_ZIP = 1000; // Limit number of entries per zip to
                                                         // prevent infinite loops

    private final ChatEntryService chatEntryService;
    private final ChatService chatService;
    private final FileNamingService fileNamingService;
    private final AttachmentService attachmentService;

    /**
     * Generate or find existing chat ID based on filename and user If the same filename was
     * uploaded before by the same user, return the existing chat ID
     */
    public String generateChatId(String originalFileName, Long userId) {
        // Create a deterministic chat ID based on filename (without timestamp and UUID)
        String filenameHash = originalFileName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String baseChatId = filenameHash;

        // Check if this user already has a chat with this filename
        List<String> existingChatIds = chatEntryService.getChatIdsForUser(userId);

        for (String existingChatId : existingChatIds) {
            // If the existing chat ID starts with our base chat ID, it's the same file
            if (existingChatId.startsWith(baseChatId + "_")) {
                log.info("Found existing chat ID: {} for filename: {} and user: {}", existingChatId,
                        originalFileName, userId);
                return existingChatId;
            }
        }

        // No existing chat found, create a new one with timestamp and UUID
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String newChatId = baseChatId + "_" + timestamp + "_" + uuid;

        log.info("Generated new chat ID: {} for filename: {} and user: {}", newChatId,
                originalFileName, userId);
        return newChatId;
    }

    /**
     * Process and upload a text chat file
     */
    public UploadResult uploadTextFile(MultipartFile file, Long userId) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Cannot get original filename");
        }

        String chatId = generateChatId(originalFileName, userId);
        log.info("Processing text file: {} with chatId: {} for user: {}", originalFileName, chatId,
                userId);

        // Check if this is a re-upload of the same file
        boolean isReupload = chatService.chatExists(userId, chatId);

        List<ChatEntry> chatEntries = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            StringBuilder currentEntry = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (isNewEntry(line)) {
                    if (!currentEntry.isEmpty()) {
                        ChatEntry entry = parseChatEntry(currentEntry.toString());
                        ChatEntryEnhancer.enhance(entry, true, true);
                        chatEntries.add(entry);
                        currentEntry.setLength(0);
                    }
                }
                currentEntry.append(line).append("\n");
            }

            // Don't forget the last entry
            if (!currentEntry.isEmpty()) {
                ChatEntry entry = parseChatEntry(currentEntry.toString());
                ChatEntryEnhancer.enhance(entry, true, true);
                chatEntries.add(entry);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error processing chat file", e);
        }

        // Handle re-upload with smart incremental update
        List<ChatEntryEntity> savedEntries;
        if (isReupload) {
            log.info("Detected re-upload of existing chat: {}. Performing incremental update.",
                    chatId);
            savedEntries = performIncrementalUpdate(chatEntries, userId, chatId);
        } else {
            // New upload - save all entries
            savedEntries = chatEntryService.saveChatEntries(chatEntries, userId, chatId);
        }

        log.info("Successfully processed {} entries for chat: {} user: {} (reupload: {})",
                savedEntries.size(), chatId, userId, isReupload);

        return UploadResult.builder().chatId(chatId).originalFileName(originalFileName)
                .fileType("TXT").totalEntries(savedEntries.size()).totalAttachments(0) // Text files
                                                                                       // don't have
                                                                                       // attachments
                .success(true).build();
    }

    /**
     * Process and upload a ZIP file containing chat and multimedia files
     */
    public UploadResult uploadZipFile(MultipartFile file, Long userId) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("Cannot get original filename");
        }

        // Add file size validation
        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size too large: " + fileSize
                    + " bytes. Maximum allowed: " + MAX_FILE_SIZE + " bytes");
        }

        String chatId = generateChatId(originalFileName, userId);
        log.info("Processing ZIP file: {} with chatId: {} for user: {} (size: {} bytes)",
                originalFileName, chatId, userId, fileSize);

        // Check if this is a re-upload of the same file
        boolean isReupload = chatService.chatExists(userId, chatId);

        List<ChatEntry> chatEntries = new ArrayList<>();
        List<String> extractedFiles = new ArrayList<>();
        Map<String, String> filenameToHashMap = new HashMap<>();
        Map<String, Location> filenameToLocationMap = new HashMap<>();

        long startTime = System.currentTimeMillis();
        int processedEntries = 0;
        int maxEntries = 1000; // Limit number of entries to prevent infinite loops

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            InputStream chatTextStream = null;

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                processedEntries++;

                // Check for too many entries (potential infinite loop)
                if (processedEntries > maxEntries) {
                    throw new RuntimeException("Too many entries in ZIP file: " + processedEntries
                            + ". Maximum allowed: " + maxEntries);
                }

                // Add timeout check every 10 entries
                if (processedEntries % 10 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > UPLOAD_REQUEST_TIMEOUT) { // 5 minutes timeout
                        throw new RuntimeException("ZIP processing timeout after 5 minutes");
                    }
                    log.info("Processing ZIP entry {}/{}: {} (elapsed: {}ms)", processedEntries,
                            "unknown", fileName, elapsed);
                }

                log.debug("Processing zip entry: {} (size: {} bytes)", fileName, entry.getSize());

                if (isTextFile(fileName)) {
                    // This is the chat text file - read it into memory for processing
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    chatTextStream = new ByteArrayInputStream(baos.toByteArray());
                    log.info("Found chat text file: {} (size: {} bytes)", fileName, baos.size());
                } else if (!entry.isDirectory()) {
                    // This is a multimedia file - extract and calculate hash
                    log.info("Processing multimedia file: {} (size: {} bytes)", fileName,
                            entry.getSize());
                    String contentHash = processMultimediaFile(zis, fileName, userId);
                    filenameToHashMap.put(fileName, contentHash);
                    extractedFiles.add(fileName);

                    // Get the location for this file
                    String clientId = "user_" + userId;
                    Location location = attachmentService.saveAttachmentWithLocation(contentHash,
                            fileName, clientId);
                    filenameToLocationMap.put(fileName, location);
                }

                zis.closeEntry();
            }

            // Process the chat text file if found
            if (chatTextStream != null) {
                processChatTextStream(chatTextStream, chatEntries, filenameToHashMap);
                log.info("Processed chat text file, found {} entries", chatEntries.size());
            } else {
                log.warn("No text file found in the ZIP archive");
            }

        } catch (IOException e) {
            log.error("Error processing ZIP file: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing ZIP file", e);
        }

        // Link attachment hashes to chat entries
        linkAttachmentHashes(chatEntries, filenameToHashMap, filenameToLocationMap);

        // Handle re-upload with smart incremental update
        List<ChatEntryEntity> savedEntries;
        if (isReupload) {
            log.info("Detected re-upload of existing chat: {}. Performing incremental update.",
                    chatId);
            savedEntries = performIncrementalUpdate(chatEntries, userId, chatId);
        } else {
            // New upload - save all entries
            log.info("Saving {} chat entries to database...", chatEntries.size());
            savedEntries = chatEntryService.saveChatEntries(chatEntries, userId, chatId);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info(
                "Successfully processed {} entries and {} multimedia files for chat: {} user: {} in {}ms (reupload: {})",
                savedEntries.size(), extractedFiles.size(), chatId, userId, totalTime, isReupload);

        return UploadResult.builder().chatId(chatId).originalFileName(originalFileName)
                .fileType("ZIP").totalEntries(savedEntries.size())
                .totalAttachments(extractedFiles.size()).extractedFiles(extractedFiles)
                .success(true).build();
    }

    private void processChatTextStream(InputStream chatTextStream, List<ChatEntry> chatEntries,
            Map<String, String> filenameToHashMap) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(chatTextStream))) {
            StringBuilder currentEntry = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (isNewEntry(line)) {
                    if (!currentEntry.isEmpty()) {
                        ChatEntry entry = parseChatEntry(currentEntry.toString());
                        ChatEntryEnhancer.enhance(entry, true, true);
                        chatEntries.add(entry);
                        currentEntry.setLength(0);
                    }
                }
                currentEntry.append(line).append("\n");
            }

            // Don't forget the last entry
            if (!currentEntry.isEmpty()) {
                ChatEntry entry = parseChatEntry(currentEntry.toString());
                ChatEntryEnhancer.enhance(entry, true, true);
                chatEntries.add(entry);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error processing chat text stream", e);
        }
    }

    private String processMultimediaFile(ZipInputStream zis, String fileName, Long userId)
            throws IOException {
        // Calculate hash while processing
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];

            int totalBytes = 0;
            int maxFileSize = 50 * 1024 * 1024; // 50MB limit per file

            int len;
            while ((len = zis.read(buffer)) > 0) {
                totalBytes += len;

                // Check file size limit
                if (totalBytes > maxFileSize) {
                    throw new RuntimeException("File too large: " + fileName + " (" + totalBytes
                            + " bytes). Maximum allowed: " + maxFileSize + " bytes");
                }

                digest.update(buffer, 0, len);
            }

            // Convert digest to hex string
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }

            String contentHash = result.toString();

            // Save attachment information to database
            try {
                String clientId = "user_" + userId; // Make clientId user-specific
                attachmentService.saveAttachmentWithLocation(contentHash, fileName, clientId);
                log.debug("Saved attachment: {} with hash: {} for user: {}", fileName, contentHash,
                        userId);
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

    private void linkAttachmentHashes(List<ChatEntry> chatEntries,
            Map<String, String> filenameToHashMap, Map<String, Location> filenameToLocationMap) {
        for (ChatEntry entry : chatEntries) {
            if (entry.getFileName() != null && !entry.getFileName().isEmpty()) {
                String hash = filenameToHashMap.get(entry.getFileName());
                if (hash != null) {
                    entry.setAttachmentHash(hash);
                    log.debug("Linked attachment hash {} to filename: {}", hash,
                            entry.getFileName());
                } else {
                    log.warn("No hash found for filename: {}", entry.getFileName());
                }
            }
        }
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
            builder.timestamp(timestamp);

            // Split remaining text
            String[] parts = entryText.substring(timestampMatcher.end()).split(": ", 2);
            if (parts.length == 2) {
                builder.author(parts[0].trim());
                String payload = parts[1].trim();

                // Check for file attachment
                String[] maybeWithAttachment = payload.split("\\s\\(file attached\\)");
                switch (maybeWithAttachment.length) {
                    case 1 -> // No file attachment
                            builder.payload(payload);
                    case 2 -> {
                        // File attachment found
                        String fileName = maybeWithAttachment[0];
                        builder.fileName(fileName);
                    }
                    case 3 -> {
                        // Multiple parts: attachment along with text
                        String fileName = maybeWithAttachment[0];
                        builder.fileName(fileName);
                        // maybeWithAttachment[1] contains "file attached" string
                        payload = maybeWithAttachment[2].substring(1); // Remove leading CR/LF
                        builder.payload(payload);
                    }
                    default -> log.warn("Unexpected format in message: {}", entryText);
                }
            } else {
                log.warn("Could not parse message {}", entryText);
            }
        }

        return builder.build();
    }

    /**
     * Perform incremental update of chat entries - Keep existing entries that are still present in
     * the new file - Remove existing entries that are no longer in the new file - Add new entries
     * that weren't in the database
     */
    private List<ChatEntryEntity> performIncrementalUpdate(List<ChatEntry> newEntries, Long userId,
            String chatId) {
        log.info("Starting incremental update for chat: {} with {} new entries", chatId,
                newEntries.size());

        // Get existing entries from database
        List<ChatEntryEntity> existingEntries = chatEntryService
                .findByUserIdAndChatId(userId, chatId, 0, Integer.MAX_VALUE).getContent();
        log.info("Found {} existing entries in database", existingEntries.size());

        // Create a map of new entries for efficient lookup
        Map<String, ChatEntry> newEntriesMap = new HashMap<>();
        for (ChatEntry entry : newEntries) {
            String key = createEntryKey(entry);
            newEntriesMap.put(key, entry);
        }

        // Find entries to keep (existing entries that are still in new file)
        List<ChatEntryEntity> entriesToKeep = new ArrayList<>();
        Set<String> keptEntryKeys = new HashSet<>();

        for (ChatEntryEntity existingEntry : existingEntries) {
            String key = createEntryKey(existingEntry);
            if (newEntriesMap.containsKey(key)) {
                entriesToKeep.add(existingEntry);
                keptEntryKeys.add(key);
                log.debug("Keeping existing entry: {}", key);
            } else {
                log.debug("Removing existing entry (no longer in new file): {}", key);
            }
        }

        // Find new entries to add (entries in new file that weren't in database)
        List<ChatEntry> entriesToAdd = new ArrayList<>();
        for (ChatEntry newEntry : newEntries) {
            String key = createEntryKey(newEntry);
            if (!keptEntryKeys.contains(key)) {
                entriesToAdd.add(newEntry);
                log.debug("Adding new entry: {}", key);
            }
        }

        // Delete all existing entries
        chatEntryService.deleteChat(userId, chatId);
        log.info("Deleted all existing entries for chat: {}", chatId);

        // Save kept entries and new entries
        List<ChatEntryEntity> savedEntries = new ArrayList<>();

        if (!entriesToKeep.isEmpty()) {
            log.info("Re-saving {} kept entries", entriesToKeep.size());
            // Convert ChatEntryEntity back to ChatEntry for saving
            List<ChatEntry> entriesToKeepAsChatEntries = entriesToKeep.stream()
                    .map(ChatEntryEntity::toChatEntry).collect(Collectors.toList());
            savedEntries.addAll(
                    chatEntryService.saveChatEntries(entriesToKeepAsChatEntries, userId, chatId));
        }

        if (!entriesToAdd.isEmpty()) {
            log.info("Saving {} new entries", entriesToAdd.size());
            savedEntries.addAll(chatEntryService.saveChatEntries(entriesToAdd, userId, chatId));
        }

        log.info(
                "Incremental update completed. Total entries: {} (kept: {}, added: {}, removed: {})",
                savedEntries.size(), entriesToKeep.size(), entriesToAdd.size(),
                existingEntries.size() - entriesToKeep.size());

        return savedEntries;
    }

    /**
     * Create a unique key for a chat entry based on its content This is used to identify duplicate
     * entries across uploads
     */
    private String createEntryKey(ChatEntry entry) {
        return String.format("%s|%s|%s|%s", entry.getTimestamp(), entry.getAuthor(),
                entry.getPayload() != null ? entry.getPayload() : "",
                entry.getFileName() != null ? entry.getFileName() : "");
    }

    /**
     * Create a unique key for a chat entry entity
     */
    private String createEntryKey(ChatEntryEntity entry) {
        return String.format("%s|%s|%s|%s", entry.getTimestamp(), entry.getAuthor(),
                entry.getPayload() != null ? entry.getPayload() : "",
                entry.getFileName() != null ? entry.getFileName() : "");
    }

    // Result class for upload operations
    public static class UploadResult {
        private String chatId;
        private String originalFileName;
        private String fileType;
        private int totalEntries;
        private int totalAttachments;
        private List<String> extractedFiles;
        private boolean success;
        private String errorMessage;

        // Builder pattern
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

        // Getters
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
