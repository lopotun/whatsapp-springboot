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
        ChatEntryEnhanced.Type res = ChatEntryEnhanced.Type.UNKNOWN;
        String fileName = chatEntry.getFileName();
        if (fileName != null && !fileName.isEmpty()) {
            String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            res = switch (extension) {
                case ".jpg", ".jpeg", ".png" -> ChatEntryEnhanced.Type.IMAGE;
                case ".mp4", ".mov" -> ChatEntryEnhanced.Type.VIDEO;
                case ".mp3", ".wav" -> ChatEntryEnhanced.Type.AUDIO;
                case ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx" -> ChatEntryEnhanced.Type.DOCUMENT;
                default -> ChatEntryEnhanced.Type.FILE;
            };
        } else {
            String payload = chatEntry.getPayload();
            if (payload != null) {
                res = switch (payload) {
                    case String p when p.startsWith("sticker:") -> ChatEntryEnhanced.Type.STICKER; //todo check if this is correct
                    case String p when p.startsWith("contact:") -> ChatEntryEnhanced.Type.CONTACT; //todo check if this is correct
                    case String p when p.startsWith("location:") -> ChatEntryEnhanced.Type.LOCATION;
                    case String p when p.startsWith("POLL:") -> ChatEntryEnhanced.Type.POLL;
                    default -> ChatEntryEnhanced.Type.TEXT;
                };
            }
        }
        return res;
    }

    private static LocalDateTime parseTimestamp(String timestampString) {
        timestampString = timestampString.replace(' ', ' ').toLowerCase(); // Replace non-breaking space with regular space
        return LocalDateTime.parse(timestampString, DATE_TIME_FORMATTER);
    }
}