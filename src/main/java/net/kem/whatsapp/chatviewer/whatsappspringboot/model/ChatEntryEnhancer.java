package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

@UtilityClass
@Builder
public class ChatEntryEnhancer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M/d/yy, h:mm a");
    private boolean timestamp;
    private boolean chatType;

    @NonNull
    public List<ChatEntryEnhanced> enhance(@NonNull List<ChatEntry> chatEntries) {
        return enhance(chatEntries, null);
    }

    @NonNull
    public List<ChatEntryEnhanced> enhance(@NonNull List<ChatEntry> chatEntries, @Nullable Predicate<ChatEntry> filter) {
        return chatEntries.stream()
                .filter(filter == null ? entry -> true : filter)
                .map(ChatEntryEnhancer::enhance)
                .toList();
    }

    @NonNull
    public ChatEntryEnhanced enhance(@NonNull ChatEntry chatEntry) {
        ChatEntryEnhanced.ChatEntryEnhancedBuilder builder = ChatEntryEnhanced.builder()
                .timestamp(chatEntry.getTimestamp())
                .payload(chatEntry.getPayload())
                .author(chatEntry.getAuthor())
                .fileName(chatEntry.getFileName());

        if (timestamp) {
            builder.localDateTime(parseTimestamp(chatEntry.getTimestamp()));
        }

        if (chatType) {
            builder.type(determineType(chatEntry));
        }
        return builder.build();
    }

    private static ChatEntryEnhanced.Type determineType(ChatEntry chatEntry) {
        return ChatEntryEnhanced.Type.UNKNOWN;
    }

    private static LocalDateTime parseTimestamp(String timestampString) {
        timestampString = timestampString.replace(' ', ' ').toLowerCase(); // Replace non-breaking space with regular space
        return LocalDateTime.parse(timestampString, DATE_TIME_FORMATTER);
    }
}