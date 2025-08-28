package net.kem.whatsapp.chatviewer.shared.constants;

/**
 * Shared constants for WhatsApp message types Used by both main service and zipper service
 */
//TODO: Remove this class and use enum Type in ChatEntry instead
public final class MessageTypes {

    private MessageTypes() {
        // Utility class, prevent instantiation
    }

    // Text messages
    public static final String TEXT = "TEXT";

    // File attachments
    public static final String FILE = "FILE";
    public static final String DOCUMENT = "DOCUMENT";

    // Media attachments
    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    public static final String AUDIO = "AUDIO";
    public static final String STICKER = "STICKER";
    public static final String GIF = "GIF";

    // Contact and location
    public static final String CONTACT = "CONTACT";
    public static final String LOCATION = "LOCATION";

    // Poll messages
    public static final String POLL = "POLL";

    // System messages
    public static final String SYSTEM = "SYSTEM";
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * Check if message type is a media type
     */
    public static boolean isMediaType(String type) {
        return IMAGE.equals(type) || VIDEO.equals(type) || AUDIO.equals(type)
                || STICKER.equals(type) || GIF.equals(type);
    }

    /**
     * Check if message type is a file type
     */
    public static boolean isFileType(String type) {
        return FILE.equals(type) || DOCUMENT.equals(type);
    }

    /**
     * Check if message type is an attachment type
     */
    public static boolean isAttachmentType(String type) {
        return isMediaType(type) || isFileType(type) || CONTACT.equals(type)
                || LOCATION.equals(type) || POLL.equals(type);
    }
}
