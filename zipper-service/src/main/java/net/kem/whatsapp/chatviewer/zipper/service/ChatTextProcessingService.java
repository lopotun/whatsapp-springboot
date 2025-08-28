package net.kem.whatsapp.chatviewer.zipper.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.shared.model.ChatEntry;

/**
 * Service for processing WhatsApp chat text files
 */
@Service
@Slf4j
public class ChatTextProcessingService {

    private static final Pattern CHAT_ENTRY_PATTERN =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2}, (\\d{1,2}):(\\d{2})) - ([^:]+): (.+)");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("M/d/yy, H:mm");

    /**
     * Process chat text stream and extract chat entries
     */
    public List<ChatEntry> processChatTextStream(InputStream chatTextStream, Long userId,
            String chatId) throws IOException {
        List<ChatEntry> entries = new ArrayList<>();
        final AtomicInteger entryCount = new AtomicInteger(0);

        // Don't use try-with-resources here since the caller (ZipInputStream) manages the stream
        // lifecycle
        BufferedReader reader = new BufferedReader(new InputStreamReader(chatTextStream));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                Matcher matcher = CHAT_ENTRY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    try {
                        String dateTimeStr = matcher.group(1);
                        String author = matcher.group(4);
                        String payload = matcher.group(5);

                        LocalDateTime localDateTime =
                                LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
                        ChatEntry.Type type = determineMessageType(payload);
                        String fileName = extractFileName(payload);// TODO: call this only if type
                                                                   // is not TEXT

                        ChatEntry entry = ChatEntry.builder().payload(payload).author(author)
                                .fileName(fileName).type(type).localDateTime(localDateTime)
                                .userId(userId).chatId(chatId).build();

                        entries.add(entry);
                        entryCount.incrementAndGet();

                        if (entryCount.get() % 1000 == 0) {
                            log.info("Processed {} chat entries", entryCount.get());
                        }

                    } catch (Exception e) {
                        log.warn("Failed to parse chat entry line: {}", line, e);
                    }
                }
            }
        } finally {
            // Close the reader but not the underlying stream
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Error closing reader: {}", e.getMessage());
            }
        }

        log.info("Successfully processed {} chat entries from chat text", entryCount.get());
        return entries;
    }

    /**
     * Link chat entries to attachments based on filename mapping
     */
    public void linkChatEntriesToAttachments(List<ChatEntry> entries,
            Map<String, String> filenameToChecksum) {
        int linkedCount = 0;
        for (ChatEntry entry : entries) {
            if (entry.getFileName() != null
                    && filenameToChecksum.containsKey(entry.getFileName())) {
                String hash = filenameToChecksum.get(entry.getFileName());
                entry.setAttachmentHash(hash);
                linkedCount++;
            }
        }
        log.info("Linked {} chat entries to attachments", linkedCount);
    }

    /**
     * Determine message type based on payload content
     */
    private ChatEntry.Type determineMessageType(String payload) {
        ChatEntry.Type res = ChatEntry.Type.TEXT;
        if (StringUtils.hasText(payload)) {
            int attachmentIndicator = payload.lastIndexOf(" (file attached)");
            if (attachmentIndicator > 0) {
                String fileName = payload.substring(0, attachmentIndicator);
                String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
                res = switch (extension) {
                    case ".jpg", ".jpeg", ".png" -> ChatEntry.Type.IMAGE;
                    case ".mp4", ".mov" -> ChatEntry.Type.VIDEO;
                    case ".aac", ".mp3", ".wav", ".opus" -> ChatEntry.Type.AUDIO;
                    case ".was" -> ChatEntry.Type.STICKER;
                    case ".vcf" -> ChatEntry.Type.CONTACT;
                    case ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx" -> ChatEntry.Type.DOCUMENT;
                    default -> ChatEntry.Type.FILE;
                };
            } else {
                if (payload.equals("<Media omitted>")) {
                    res = ChatEntry.Type.REMOVED_MEDIA;
                }
            }
        } else {
            if (payload != null) {
                res = switch (payload) {
                    case String p when p.startsWith("location:") -> ChatEntry.Type.LOCATION;
                    case String p when p.startsWith("POLL:") -> ChatEntry.Type.POLL;
                    default -> ChatEntry.Type.TEXT;
                };
            }
        }
        return res;
    }

    /**
     * Extract filename from attachment message
     */
    private String extractFileName(String payload) {
        if (payload == null) {
            return null;
        }

        // Check for new WhatsApp format: IMG-20250723-WA0001.jpg (file attached)
        if (payload.contains("(file attached)")) {
            int endIndex = payload.indexOf(" (file attached)");
            if (endIndex > 0) {
                return payload.substring(0, endIndex).trim();
            }
        }

        // Check for old format: <attached:filename>
        if (payload.contains("<attached:")) {
            int startIndex = payload.indexOf("<attached:") + 10;
            int endIndex = payload.indexOf(">", startIndex);
            if (endIndex > startIndex) {
                return payload.substring(startIndex, endIndex).trim();
            }
        }

        return null;
    }
}
