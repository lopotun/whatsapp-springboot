package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Builder
@Getter
@lombok.extern.jackson.Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEntry {
    protected final String timestamp;
    protected final String payload;
    protected final String author;
    protected final String fileName;
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String attachmentHash;

    public enum Type {TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, LOCATION, CONTACT, POLL, STICKER, UNKNOWN}
    // Setters for fields that are set during parsing
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Type type;
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime localDateTime;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ChatEntry(");
        sb.append("timestamp=").append(timestamp);
        sb.append(", author=").append(author);
        sb.append(", payload=").append(payload);
        sb.append(", fileName=").append(fileName);
        if (type != null) {
            sb.append(", type=").append(type);
        }
        if (localDateTime != null) {
            sb.append(", localDateTime=").append(localDateTime);
        }
        if (attachmentHash != null) {
            sb.append(", attachmentHash=").append(attachmentHash);
        }
        sb.append(")");
        return sb.toString();
    }
}