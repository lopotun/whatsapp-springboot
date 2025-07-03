package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class ChatEntryEnhanced extends ChatEntry {
    public enum Type {TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, LOCATION, CONTACT, POLL, STICKER, UNKNOWN}

    private LocalDateTime localDateTime;
    private Type type;
}