package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEnhancer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    void shouldParseRegularMessage() {
        String chat = "9/4/23, 7:34 - Eugene Kurtzer: Test.\n";
        List<ChatEntry> entries = processChat(chat);

        assertEquals(1, entries.size());
        ChatEntry entry = entries.getFirst();
        assertEquals("9/4/23, 7:34", entry.getTimestamp());
        assertEquals("Eugene Kurtzer", entry.getAuthor());
        assertEquals("Test.", entry.getPayload());
        assertNull(entry.getFileName());
    }

    @Test
    void shouldParseFileAttachment() {
        String chat = "11/5/23, 1:40 - Eugene Kurtzer: IMG-20231105-WA0008.jpg (file attached)\n";
        List<ChatEntry> entries = processChat(chat);

        assertEquals(1, entries.size());
        ChatEntry entry = entries.getFirst();
        assertEquals("11/5/23, 1:40", entry.getTimestamp());
        assertEquals("Eugene Kurtzer", entry.getAuthor());
        assertNull(entry.getPayload());
        assertEquals("IMG-20231105-WA0008.jpg", entry.getFileName());
    }

    @Test
    void shouldParseFileAttachmentWithTest() {
        String chat = "11/5/23, 1:40 - Eugene Kurtzer: IMG-20231105-WA0008.jpg (file attached)\nSome test.";
        List<ChatEntry> entries = processChat(chat);

        assertEquals(1, entries.size());
        ChatEntry entry = entries.getFirst();
        assertEquals("11/5/23, 1:40", entry.getTimestamp());
        assertEquals("Eugene Kurtzer", entry.getAuthor());
        assertEquals("Some test.", entry.getPayload());
        assertEquals("IMG-20231105-WA0008.jpg", entry.getFileName());
    }

    @Test
    void shouldParseMultilineMessage() {
        String chat = """
            11/16/23, 10:32 - Eugene Kurtzer: В 08:40
            1. Показать результаты МРТ -- невропатолог написал, что нужно сделать анализы крови.
            2. Взять направление к эндокринологу.
            3. Продлить направление на анализ крови.
            4. В секретариате заказать очередь на воскресенье на 8:00 утра.
            """;
        List<ChatEntry> entries = processChat(chat);

        assertEquals(1, entries.size());
        ChatEntry entry = entries.getFirst();
        assertEquals("11/16/23, 10:32", entry.getTimestamp());
        assertEquals("Eugene Kurtzer", entry.getAuthor());
        assertTrue(entry.getPayload().contains("В 08:40"));
        assertTrue(entry.getPayload().contains("1. Показать результаты МРТ"));
        assertNull(entry.getFileName());
    }

    @Test
    void shouldParseMultipleMessages() {
        String chat = """
            9/4/23, 7:34 - Eugene Kurtzer: Test.
            11/5/23, 1:40 - Eugene Kurtzer: IMG-20231105-WA0008.jpg (file attached)
            11/16/23, 10:32 - Eugene Kurtzer: Multi
            line
            message
            """;
        List<ChatEntry> entries = processChat(chat);

        assertEquals(3, entries.size());
        assertEquals("Test.", entries.get(0).getPayload());
        assertEquals("IMG-20231105-WA0008.jpg", entries.get(1).getFileName());
        assertEquals("Multi\nline\nmessage", entries.get(2).getPayload());
    }


    @Disabled
    @Test
    void shouldParseMultipleMessagesFromFile() {
        try(InputStream is = new FileInputStream("src/test/resources/WhatsAppChat.txt")) {
            List<ChatEntry> entries = processChat(is);

            assertEquals(271, entries.size());
            assertEquals(35, entries.stream().filter(e -> e.getFileName() != null).count());

            // Test the enhancement logic
            List<ChatEntry> enhanced = ChatEntryEnhancer.enhance(entries, true, true);
            assertEquals(5, enhanced.stream().filter(e -> e.getType() == ChatEntry.Type.DOCUMENT).count());
            assertEquals(3, enhanced.stream().filter(e -> e.getType() == ChatEntry.Type.LOCATION).count());
            assertEquals(1, enhanced.stream().filter(e -> e.getType() == ChatEntry.Type.CONTACT).count());
            assertEquals(1, enhanced.stream().filter(e -> e.getType() == ChatEntry.Type.POLL).count());
            assertEquals(1, enhanced.stream().filter(e -> e.getType() == ChatEntry.Type.STICKER).count());
        } catch (Exception e) {
            fail("Failed to read chat file: " + e.getMessage());
        }
    }

    private List<ChatEntry> processChat(String chat) {
        return processChat(new ByteArrayInputStream(chat.getBytes(StandardCharsets.UTF_8)));
    }

    private List<ChatEntry> processChat(InputStream inputStream) {
        List<ChatEntry> entries = new ArrayList<>();
        chatService.streamChatFile(inputStream, entries::add);
        return entries;
    }
}