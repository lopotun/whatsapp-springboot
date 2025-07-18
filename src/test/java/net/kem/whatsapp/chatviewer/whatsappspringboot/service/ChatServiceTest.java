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
import java.util.HashMap;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.profiles.active=test")
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

    @Test
    void processZipFile_shouldExtractFilesAndProcessChat() throws Exception {
        // Create a test zip file with a chat text file and a multimedia file
        byte[] zipContent = createTestZipWithChatAndMedia();
        
        List<String> extractedFiles = new ArrayList<>();
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            extractedFiles = result;
        }
        
        // Verify that files were extracted
        assertThat(extractedFiles).isNotEmpty();
        
        // Verify that chat entries were processed
        assertThat(processedEntries).isNotEmpty();
        
        // DEBUG: Print database contents
        System.out.println("=== DATABASE CONTENTS ===");
        System.out.println("Processed entries: " + processedEntries.size());
        processedEntries.forEach(entry -> 
            System.out.println("Entry: " + entry.getAuthor() + " - " + entry.getPayload()));
        System.out.println("Extracted files: " + extractedFiles.size());
        extractedFiles.forEach(file -> System.out.println("File: " + file));
        System.out.println("========================");
        
        // Clean up extracted files
        for (String filePath : extractedFiles) {
            Files.deleteIfExists(Paths.get(filePath));
        }
    }

    @Test
    void processZipFile_shouldHandleZipWithOnlyTextFile() throws Exception {
        // Create a zip file with only a text file
        byte[] zipContent = createZipWithOnlyTextFile();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have no extracted files since there are no media files
            assertThat(result).isEmpty();
            
            // Should have processed chat entries
            assertThat(processedEntries).hasSize(1);
            assertThat(processedEntries.get(0).getAuthor()).isEqualTo("Test User");
            assertThat(processedEntries.get(0).getPayload()).isEqualTo("Hello World");
        }
    }

    @Test
    void processZipFile_shouldHandleZipWithOnlyMediaFiles() throws Exception {
        // Create a zip file with only media files
        byte[] zipContent = createZipWithOnlyMediaFiles();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have extracted media files
            assertThat(result).hasSize(2);
            
            // Should have no processed entries since there's no text file
            assertThat(processedEntries).isEmpty();
            
            // Clean up extracted files
            for (String filePath : result) {
                Files.deleteIfExists(Paths.get(filePath));
            }
        }
    }

    @Test
    void processZipFile_shouldHandleEmptyZip() throws Exception {
        // Create an empty zip file
        byte[] zipContent = createEmptyZip();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have no extracted files
            assertThat(result).isEmpty();
            
            // Should have no processed entries
            assertThat(processedEntries).isEmpty();
        }
    }

    @Test
    void processZipFile_shouldHandleZipWithMultipleTextFiles() throws Exception {
        // Create a zip file with multiple text files
        byte[] zipContent = createZipWithMultipleTextFiles();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have no extracted files since there are no media files
            assertThat(result).isEmpty();
            
            // Should have processed chat entries from the first text file found
            assertThat(processedEntries).isNotEmpty();
        }
    }

    @Test
    void processZipFile_shouldHandleZipWithDirectories() throws Exception {
        // Create a zip file with directories and files
        byte[] zipContent = createZipWithDirectories();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have extracted media files (directories should be ignored)
            assertThat(result).hasSize(1);
            
            // Should have processed chat entries
            assertThat(processedEntries).isNotEmpty();
            
            // Clean up extracted files
            for (String filePath : result) {
                Files.deleteIfExists(Paths.get(filePath));
            }
        }
    }

    @Disabled
    @Test
    void processZipFile_shouldHandleInvalidZipFile() throws IOException {
        // Create invalid zip content that will cause ZipInputStream to fail
        byte[] invalidZipContent = new byte[100];
        // Fill with random data that's not a valid ZIP structure
        for (int i = 0; i < invalidZipContent.length; i++) {
            invalidZipContent[i] = (byte) (i % 256);
        }
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidZipContent)) {
            assertThatThrownBy(() -> {
                chatService.processZipFile(inputStream, entry -> {});
            }).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Error processing zip file");
        }
    }

    @Test
    void processZipFile_shouldHandleNullInputStream() {
        assertThatThrownBy(() -> {
            chatService.processZipFile(null, entry -> {});
        }).isInstanceOf(NullPointerException.class);
    }

    @Test
    void processZipFile_shouldHandleNullConsumer() throws IOException {
        byte[] zipContent = createTestZipWithChatAndMedia();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            assertThatThrownBy(() -> {
                chatService.processZipFile(inputStream, null);
            }).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void processZipFile_shouldHandleConsumerThrowingException() throws IOException {
        byte[] zipContent = createTestZipWithChatAndMedia();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            assertThatThrownBy(() -> {
                chatService.processZipFile(inputStream, entry -> {
                    throw new RuntimeException("Consumer error");
                });
            }).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Consumer error");
        }
    }

    @Test
    void processZipFile_shouldHandleLargeMediaFiles() throws Exception {
        // Create a zip file with a large media file
        byte[] zipContent = createZipWithLargeMediaFile();
        
        List<ChatEntry> processedEntries = new ArrayList<>();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent)) {
            List<String> result = chatService.processZipFile(inputStream, entry -> {
                processedEntries.add(entry);
            });
            
            // Should have extracted the large media file
            assertThat(result).hasSize(1);
            
            // Should have processed chat entries
            assertThat(processedEntries).isNotEmpty();
            
            // Clean up extracted files
            for (String filePath : result) {
                Files.deleteIfExists(Paths.get(filePath));
            }
        }
    }
    
    private byte[] createZipWithOnlyTextFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry chatEntry = new ZipEntry("chat.txt");
            zos.putNextEntry(chatEntry);
            String chatContent = "6/21/24, 7:19 - Test User: Hello World\n";
            zos.write(chatContent.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithOnlyMediaFiles() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add image file
            ZipEntry imageEntry = new ZipEntry("image1.jpg");
            zos.putNextEntry(imageEntry);
            zos.write("fake image content 1".getBytes());
            zos.closeEntry();
            
            // Add video file
            ZipEntry videoEntry = new ZipEntry("video1.mp4");
            zos.putNextEntry(videoEntry);
            zos.write("fake video content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createEmptyZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Empty zip file
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithMultipleTextFiles() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // First text file
            ZipEntry chatEntry1 = new ZipEntry("chat1.txt");
            zos.putNextEntry(chatEntry1);
            String chatContent1 = "6/21/24, 7:19 - User1: Hello from file 1\n";
            zos.write(chatContent1.getBytes());
            zos.closeEntry();
            
            // Second text file
            ZipEntry chatEntry2 = new ZipEntry("chat2.txt");
            zos.putNextEntry(chatEntry2);
            String chatContent2 = "6/21/24, 7:20 - User2: Hello from file 2\n";
            zos.write(chatContent2.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithDirectories() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add a directory entry
            ZipEntry dirEntry = new ZipEntry("images/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            
            // Add a file in the directory
            ZipEntry imageEntry = new ZipEntry("images/test.jpg");
            zos.putNextEntry(imageEntry);
            zos.write("fake image content".getBytes());
            zos.closeEntry();
            
            // Add a text file
            ZipEntry chatEntry = new ZipEntry("chat.txt");
            zos.putNextEntry(chatEntry);
            String chatContent = "6/21/24, 7:19 - Test User: Hello World\n";
            zos.write(chatContent.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createZipWithLargeMediaFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add a large media file (1MB of data)
            ZipEntry largeEntry = new ZipEntry("large-video.mp4");
            zos.putNextEntry(largeEntry);
            byte[] largeContent = new byte[1024 * 1024]; // 1MB
            for (int i = 0; i < largeContent.length; i++) {
                largeContent[i] = (byte) (i % 256);
            }
            zos.write(largeContent);
            zos.closeEntry();
            
            // Add a text file
            ZipEntry chatEntry = new ZipEntry("chat.txt");
            zos.putNextEntry(chatEntry);
            String chatContent = "6/21/24, 7:19 - Test User: Hello World\n";
            zos.write(chatContent.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    private byte[] createTestZipWithChatAndMedia() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add a chat text file
            ZipEntry chatEntry = new ZipEntry("chat.txt");
            zos.putNextEntry(chatEntry);
            String chatContent = "6/21/24, 7:19 - Test User: Hello World\n";
            zos.write(chatContent.getBytes());
            zos.closeEntry();
            
            // Add a multimedia file
            ZipEntry mediaEntry = new ZipEntry("test-image.jpg");
            zos.putNextEntry(mediaEntry);
            zos.write("fake image content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private List<ChatEntry> processChat(String chat) {
        return processChat(new ByteArrayInputStream(chat.getBytes(StandardCharsets.UTF_8)));
    }

    private List<ChatEntry> processChat(InputStream inputStream) {
        List<ChatEntry> entries = new ArrayList<>();
        chatService.streamChatFile(inputStream, entries::add, new HashMap<>());
        return entries;
    }
}