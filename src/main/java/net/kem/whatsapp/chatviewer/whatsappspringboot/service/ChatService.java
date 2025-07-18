package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEnhancer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class ChatService {
    // private static final Pattern TIMESTAMP_PATTERN_AMPM =
            // Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s+\\d{1,2}:\\d{2} +[AP]M)\\s-\\s");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s\\d{1,2}:\\d{2})\\s-\\s");

    @Value("${app.multimedia.storage.path}")
    private String multimediaStoragePath;

    private Path tempDir;

    public ChatService(@Autowired FileNamingService fileNamingService, @Autowired AttachmentService attachmentService, @Autowired ChatEntryService chatEntryService) {
        this.fileNamingService = fileNamingService;
        this.attachmentService = attachmentService;
        this.chatEntryService = chatEntryService;
    }

    @PostConstruct
    public void init() {
        tempDir = Paths.get(multimediaStoragePath, "temp");
        try {
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.debug("Created temp directory: {}", tempDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }

    private final FileNamingService fileNamingService;
    private final AttachmentService attachmentService;
    private final ChatEntryService chatEntryService;

    public void streamChatFile(InputStream inputStream, Consumer<ChatEntry> entryConsumer, Map<String, String> filenameToHashMap) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder currentEntry = new StringBuilder();
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                if (isNewEntry(line)) {
                    if (!currentEntry.isEmpty()) {
                        ChatEntry entry = parseChatEntry(currentEntry.toString());
                        // Enhance the entry with timestamp and type
                        ChatEntryEnhancer.enhance(entry, true, true);
                        // Link attachment hash if filename exists in the mapping
                        linkAttachmentHash(entry, filenameToHashMap);
                        // Save to database
                        chatEntryService.saveChatEntry(entry);
                        // Stream to consumer
                        entryConsumer.accept(entry);
                        currentEntry.setLength(0);
                    }
                }
                currentEntry.append(line).append("\n");
            }

            // Don't forget the last entry
            if (!currentEntry.isEmpty()) {
                ChatEntry entry = parseChatEntry(currentEntry.toString());
                // Enhance the entry with timestamp and type
                ChatEntryEnhancer.enhance(entry, true, true);
                // Link attachment hash if filename exists in the mapping
                linkAttachmentHash(entry, filenameToHashMap);
                // Save to database
                chatEntryService.saveChatEntry(entry);
                // Stream to consumer
                entryConsumer.accept(entry);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error processing chat file", e);
        }
    }

    private boolean isNewEntry(String line) {
        return TIMESTAMP_PATTERN.matcher(line).lookingAt();
    }

    private ChatEntry parseChatEntry(String entryText) {
        ChatEntry.ChatEntryBuilder builder = ChatEntry.builder();
        // Parse timestamp
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(entryText);
        if (timestampMatcher.find()) {
            String timestamp = timestampMatcher.group(1);
            builder.timestamp(timestamp);

            // Split remaining text
            String[] parts = entryText.substring(timestampMatcher.end()).split(": ", 2);
            if (parts.length == 2) {
                builder.author(parts[0].trim());
                String payload = parts[1].trim();

                // Check for file attachment
                String[] maybeWithAttachment = payload.splitWithDelimiters("\\s\\(file attached\\)", 0);
                switch (maybeWithAttachment.length) {
                    case 1 -> // No file attachment
                            builder.payload(payload);
                    case 2 -> {
                        // File attachment found
                        String fileName = maybeWithAttachment[0];
                        builder.fileName(fileName);
                    }
                    case 3 -> {
                        // Multiple parts: attachment along with test
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
     * Links attachment hash to chat entry if filename exists in the mapping
     */
    private void linkAttachmentHash(ChatEntry entry, Map<String, String> filenameToHashMap) {
        if (entry.getFileName() != null && !entry.getFileName().isEmpty()) {
            String hash = filenameToHashMap.get(entry.getFileName());
            if (hash != null) {
                // Set the attachment hash on the entry
                entry.setAttachmentHash(hash);
                log.debug("Linked attachment hash {} to filename: {}", hash, entry.getFileName());
            } else {
                log.warn("No hash found for filename: {}", entry.getFileName());
            }
        }
    }

    /**
     * Processes a zip file containing multimedia files and a WhatsApp chat text file.
     * Extracts multimedia files to content-based locations using SHA-256 hashing.
     * 
     * @param zipInputStream The zip file input stream
     * @param entryConsumer Consumer to process chat entries
     * @return List of extracted multimedia file paths
     */
    public List<String> processZipFile(InputStream zipInputStream, Consumer<ChatEntry> entryConsumer) {
        List<String> extractedFiles = new ArrayList<>();
        Map<String, String> filenameToHashMap = new HashMap<>(); // Thread-local mapping for this processing session
        
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            InputStream chatTextStream = null;
            
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                log.debug("Processing zip entry: {}", fileName);
                
                if (isTextFile(fileName)) {
                    // This is the chat text file - read it into memory for processing
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    chatTextStream = new ByteArrayInputStream(baos.toByteArray());
                } else if (!entry.isDirectory()) {
                    // This is a multimedia file - extract to temp location and calculate hash simultaneously
                    Path tempFile = tempDir.resolve(fileName);
                    
                    // Calculate hash while copying to temp location
                    String contentHash = copyAndCalculateHash(zis, tempFile);
                    log.debug("Copied file to temp location: {} with hash: {}", tempFile, contentHash);
                    
                    // Store filename to hash mapping for later linking
                    filenameToHashMap.put(fileName, contentHash);
                    
                    // Move to final content-based location using pre-calculated hash
                    try {
                        Path finalPath = fileNamingService.moveToFinalLocationWithHash(tempFile, fileName, contentHash);
                        extractedFiles.add(finalPath.toString());
                        log.info("Extracted multimedia file: {}", finalPath);
                        
                        // Save attachment information to database
                        // For now, using a default client ID - you might want to pass this as a parameter
                        String clientId = "default-client";
                        attachmentService.saveAttachmentWithLocation(contentHash, fileName, clientId);
                        log.debug("Saved attachment info to database for file: {}", fileName);
                    } catch (IOException e) {
                        log.error("Failed to move file {} to final location", fileName, e);
                        // Clean up temp file
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (IOException cleanupEx) {
                            log.warn("Failed to cleanup temp file: {}", tempFile, cleanupEx);
                        }
                    }
                }
                
                zis.closeEntry();
            }
            
            // Process the chat text file if found, passing the filename-to-hash mapping
            if (chatTextStream != null) {
                streamChatFile(chatTextStream, entryConsumer, filenameToHashMap);
            } else {
                log.warn("No text file found in the zip archive");
            }
            
        } catch (IOException e) {
            throw new RuntimeException("Error processing zip file", e);
        }
        
        return extractedFiles;
    }
    
    /**
     * Determines if a file is a text file based on its extension.
     */
    private boolean isTextFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".txt") || 
               lowerFileName.endsWith(".text") || 
               lowerFileName.endsWith(".log");
    }
    
    /**
     * Copies data from ZipInputStream to a file while calculating SHA-256 hash.
     * 
     * @param zis The ZipInputStream to read from
     * @param targetFile The target file to write to
     * @return The SHA-256 hash of the content
     * @throws IOException If I/O operations fail
     */
    private String copyAndCalculateHash(ZipInputStream zis, Path targetFile) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            
            // Ensure parent directory exists
            Path parentDir = targetFile.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            try (OutputStream out = Files.newOutputStream(targetFile)) {
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                    digest.update(buffer, 0, len);
                }
            }
            
            // Convert digest to hex string
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}