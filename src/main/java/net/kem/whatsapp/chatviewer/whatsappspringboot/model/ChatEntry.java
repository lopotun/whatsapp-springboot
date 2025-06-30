package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ChatEntry {
    protected final String timestamp;
    protected final String payload;
    protected final String author;
    protected final String fileName;
}