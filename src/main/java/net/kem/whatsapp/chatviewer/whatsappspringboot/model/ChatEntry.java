package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Builder
@Getter
@EqualsAndHashCode(of = {"localDateTime", "author", "payload", "fileName"})
@lombok.extern.jackson.Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEntry implements Comparable<ChatEntry> {
    protected final String payload;
    protected final String author;
    protected final String fileName;

    public enum Type {
        TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, LOCATION, CONTACT, POLL, STICKER, UNKNOWN
    }

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
        sb.append("author=").append(author);
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

    @Override
    public int compareTo(@NonNull ChatEntry other) {
        if (this.localDateTime == null && other.localDateTime == null) {
            return 0;
        }
        if (this.localDateTime == null) {
            return -1;
        }
        if (other.localDateTime == null) {
            return 1;
        }
        return this.localDateTime.compareTo(other.localDateTime);
    }
}
