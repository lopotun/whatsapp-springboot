package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

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

    public enum Type {TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, LOCATION, CONTACT, POLL, STICKER, UNKNOWN}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Type type;
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
        sb.append(")");
        return sb.toString();
    }
}