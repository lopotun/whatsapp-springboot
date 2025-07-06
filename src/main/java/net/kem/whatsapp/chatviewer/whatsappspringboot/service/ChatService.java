package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatService {
    private static final Pattern TIMESTAMP_PATTERN_AMPM =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s+\\d{1,2}:\\d{2} +[AP]M)\\s-\\s");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2},\\s\\d{1,2}:\\d{2})\\s-\\s");

    public void streamChatFile(InputStream inputStream, Consumer<ChatEntry> entryConsumer) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder currentEntry = new StringBuilder();
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                if (isNewEntry(line)) {
                    if (!currentEntry.isEmpty()) {
                        ChatEntry entry = parseChatEntry(currentEntry.toString());
                        entryConsumer.accept(entry);
                        currentEntry.setLength(0);
                    }
                }
                currentEntry.append(line).append("\n");
            }

            // Don't forget the last entry
            if (!currentEntry.isEmpty()) {
                ChatEntry entry = parseChatEntry(currentEntry.toString());
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
}