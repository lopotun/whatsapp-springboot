package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

@UtilityClass
public class ChatEntryEnhancer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M/d/yy, hh:mm");

    @NonNull
    public List<ChatEntry> enhance(@NonNull List<ChatEntry> chatEntries, boolean timestamp, boolean chatType) {
        return enhance(chatEntries, timestamp, chatType, null);
    }

    @NonNull
    public List<ChatEntry> enhance(@NonNull List<ChatEntry> chatEntries, boolean timestamp, boolean chatType, @Nullable Predicate<ChatEntry> filter) {
        return chatEntries.stream()
                .filter(filter == null ? entry -> true : filter)
                .map(entry -> enhance(entry, timestamp, chatType))
                .toList();
    }

    @NonNull
    public ChatEntry enhance(@NonNull ChatEntry chatEntry, boolean timestamp, boolean chatType) {
        ChatEntry.ChatEntryBuilder builder = ChatEntry.builder()
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

    private static ChatEntry.Type determineType(ChatEntry chatEntry) {
        ChatEntry.Type res = ChatEntry.Type.UNKNOWN;
        String fileName = chatEntry.getFileName();
        if (fileName != null && !fileName.isEmpty()) {
            String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            res = switch (extension) {
                case ".jpg", ".jpeg", ".png" -> ChatEntry.Type.IMAGE;
                case ".mp4", ".mov" -> ChatEntry.Type.VIDEO;
                case ".mp3", ".wav" -> ChatEntry.Type.AUDIO;
                case ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx" -> ChatEntry.Type.DOCUMENT;
                default -> ChatEntry.Type.FILE;
            };
        } else {
            String payload = chatEntry.getPayload();
            if (payload != null) {
                res = switch (payload) {
                    case String p when p.startsWith("sticker:") -> ChatEntry.Type.STICKER; //todo check if this is correct
                    case String p when p.startsWith("contact:") -> ChatEntry.Type.CONTACT; //todo check if this is correct
                    case String p when p.startsWith("location:") -> ChatEntry.Type.LOCATION;
                    case String p when p.startsWith("POLL:") -> ChatEntry.Type.POLL;
                    default -> ChatEntry.Type.TEXT;
                };
            }
        }
        return res;
    }

    private static LocalDateTime parseTimestamp(String timestampString) {
//        timestampString = timestampString.replace(' ', ' ').toLowerCase(); // Replace non-breaking space with regular space
        return LocalDateTime.parse(timestampString, DATE_TIME_FORMATTER);
    }
}