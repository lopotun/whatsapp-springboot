package net.kem.whatsapp.chatviewer.whatsappspringboot.model;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@lombok.extern.jackson.Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEntry implements Comparable<ChatEntry> {
    protected final String timestamp;
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

    @Override
    public int compareTo(ChatEntry other) {
        if (other == null) {
            return 1; // null values are considered greater
        }

        // Compare timestamp first
        int timestampCompare = compareStrings(this.timestamp, other.timestamp);
        if (timestampCompare != 0) {
            return timestampCompare;
        }

        // If timestamps are equal, compare author
        int authorCompare = compareStrings(this.author, other.author);
        if (authorCompare != 0) {
            return authorCompare;
        }

        // If authors are equal, compare payload
        int payloadCompare = compareStrings(this.payload, other.payload);
        if (payloadCompare != 0) {
            return payloadCompare;
        }

        // If payloads are equal, compare fileName
        return compareStrings(this.fileName, other.fileName);
    }

    /**
     * Helper method to compare strings, handling null values
     */
    private int compareStrings(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return 0;
        }
        if (str1 == null) {
            return -1; // null is considered less than non-null
        }
        if (str2 == null) {
            return 1; // non-null is considered greater than null
        }
        return str1.compareTo(str2);
    }
}
