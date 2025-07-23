package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;

@ExtendWith(MockitoExtension.class)
class ChatUploadServiceTest {

    @Mock
    private ChatEntryService chatEntryService;

    @Mock
    private ChatService chatService;

    @Mock
    private FileNamingService fileNamingService;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private ChatUploadService chatUploadService;

    private final Long userId = 1L;
    private final String chatId = "testchat_123456_abc123";

    @BeforeEach
    void setUp() {
        // Mock the chat ID generation to return consistent chat ID for same filename
        when(chatEntryService.getChatIdsForUser(userId)).thenReturn(Arrays.asList(chatId));
    }

    @Test
    void uploadTextFile_IncrementalUpdate_ShouldKeepExistingAndAddNewEntries() throws Exception {
        // Given - First upload with entries A, B, C, D
        String firstUploadContent = "12/25/23, 14:30 - John Doe: Hello, world!\n"
                + "12/25/23, 14:31 - Jane Doe: Hi there!\n"
                + "12/25/23, 14:32 - John Doe: How are you?\n"
                + "12/25/23, 14:33 - Jane Doe: I'm good!";

        MockMultipartFile firstFile = new MockMultipartFile("file", "test.txt", "text/plain",
                firstUploadContent.getBytes());

        // Mock that chat doesn't exist initially
        when(chatService.chatExists(userId, chatId)).thenReturn(false);

        // Mock the save operation for first upload
        List<ChatEntryEntity> firstSavedEntries = Arrays.asList(
                createTestChatEntryEntity(1L, "12/25/23, 14:30", "John Doe", "Hello, world!"),
                createTestChatEntryEntity(2L, "12/25/23, 14:31", "Jane Doe", "Hi there!"),
                createTestChatEntryEntity(3L, "12/25/23, 14:32", "John Doe", "How are you?"),
                createTestChatEntryEntity(4L, "12/25/23, 14:33", "Jane Doe", "I'm good!"));
        when(chatEntryService.saveChatEntries(anyList(), eq(userId), eq(chatId)))
                .thenReturn(firstSavedEntries);

        // When - First upload
        ChatUploadService.UploadResult firstResult =
                chatUploadService.uploadTextFile(firstFile, userId);

        // Then - First upload should save all entries
        assertNotNull(firstResult);
        assertTrue(firstResult.isSuccess());
        assertEquals(4, firstResult.getTotalEntries());

        // Given - Second upload with entries A, C, D, E (B removed, E added)
        String secondUploadContent = "12/25/23, 14:30 - John Doe: Hello, world!\n"
                + "12/25/23, 14:32 - John Doe: How are you?\n"
                + "12/25/23, 14:33 - Jane Doe: I'm good!\n" + "12/25/23, 14:34 - John Doe: Great!";

        MockMultipartFile secondFile = new MockMultipartFile("file", "test.txt", // Same filename
                "text/plain", secondUploadContent.getBytes());

        // Mock that chat exists for re-upload
        when(chatService.chatExists(userId, chatId)).thenReturn(true);

        // Mock existing entries in database (A, B, C, D)
        List<ChatEntryEntity> existingEntries = Arrays.asList(
                createTestChatEntryEntity(1L, "12/25/23, 14:30", "John Doe", "Hello, world!"),
                createTestChatEntryEntity(2L, "12/25/23, 14:31", "Jane Doe", "Hi there!"),
                createTestChatEntryEntity(3L, "12/25/23, 14:32", "John Doe", "How are you?"),
                createTestChatEntryEntity(4L, "12/25/23, 14:33", "Jane Doe", "I'm good!"));
        Page<ChatEntryEntity> existingPage =
                new PageImpl<>(existingEntries, PageRequest.of(0, 100), existingEntries.size());
        when(chatEntryService.findByUserIdAndChatId(userId, chatId, 0, Integer.MAX_VALUE))
                .thenReturn(existingPage);

        // Mock the save operations for incremental update
        List<ChatEntryEntity> keptEntries = Arrays.asList(
                createTestChatEntryEntity(1L, "12/25/23, 14:30", "John Doe", "Hello, world!"),
                createTestChatEntryEntity(3L, "12/25/23, 14:32", "John Doe", "How are you?"),
                createTestChatEntryEntity(4L, "12/25/23, 14:33", "Jane Doe", "I'm good!"));
        List<ChatEntryEntity> newEntries = Arrays
                .asList(createTestChatEntryEntity(5L, "12/25/23, 14:34", "John Doe", "Great!"));

        when(chatEntryService.saveChatEntries(anyList(), eq(userId), eq(chatId)))
                .thenReturn(keptEntries).thenReturn(newEntries);

        // When - Second upload (incremental update)
        ChatUploadService.UploadResult secondResult =
                chatUploadService.uploadTextFile(secondFile, userId);

        // Then - Second upload should result in A, C, D, E (B removed, E added)
        assertNotNull(secondResult);
        assertTrue(secondResult.isSuccess());
        assertEquals(4, secondResult.getTotalEntries());

        // Verify that incremental update was performed
        verify(chatService).chatExists(userId, chatId);
        verify(chatEntryService).findByUserIdAndChatId(userId, chatId, 0, Integer.MAX_VALUE);
        verify(chatEntryService).deleteChat(userId, chatId);

        // Verify that kept entries and new entries were saved
        verify(chatEntryService, times(3)).saveChatEntries(anyList(), eq(userId), eq(chatId));
    }

    @Test
    void uploadTextFile_NewUpload_ShouldNotPerformIncrementalUpdate() throws Exception {
        // Given
        String testContent = "12/25/23, 14:30 - John Doe: Hello, world!";
        MockMultipartFile file =
                new MockMultipartFile("file", "newchat.txt", "text/plain", testContent.getBytes());

        // Mock that chat does not exist (new upload scenario)
        when(chatService.chatExists(userId, anyString())).thenReturn(false);

        // Mock the save operation
        List<ChatEntryEntity> savedEntries = Arrays.asList(
                createTestChatEntryEntity(1L, "12/25/23, 14:30", "John Doe", "Hello, world!"));
        when(chatEntryService.saveChatEntries(anyList(), eq(userId), anyString()))
                .thenReturn(savedEntries);

        // When
        ChatUploadService.UploadResult result = chatUploadService.uploadTextFile(file, userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalEntries());

        // Verify that no incremental update was performed for new upload
        verify(chatService).chatExists(userId, anyString());
        verify(chatEntryService, never()).findByUserIdAndChatId(anyLong(), anyString(), anyInt(),
                anyInt());
        verify(chatEntryService, never()).deleteChat(anyLong(), anyString());
        verify(chatEntryService).saveChatEntries(anyList(), eq(userId), anyString());
    }

    @Test
    void generateChatId_SameFilename_ShouldReturnSameChatId() {
        // Given
        String filename = "test_chat.txt";
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList("testchat_123456_abc123"));

        // When
        String chatId1 = chatUploadService.generateChatId(filename, userId);
        String chatId2 = chatUploadService.generateChatId(filename, userId);

        // Then
        assertEquals(chatId1, chatId2);
        assertTrue(chatId1.startsWith("testchat_"));
    }

    @Test
    void generateChatId_DifferentFilename_ShouldReturnDifferentChatId() {
        // Given
        String filename1 = "chat1.txt";
        String filename2 = "chat2.txt";
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList("chat1_123456_abc123"));

        // When
        String chatId1 = chatUploadService.generateChatId(filename1, userId);
        String chatId2 = chatUploadService.generateChatId(filename2, userId);

        // Then
        assertNotEquals(chatId1, chatId2);
        assertTrue(chatId1.startsWith("chat1_"));
        assertTrue(chatId2.startsWith("chat2_"));
    }

    private ChatEntryEntity createTestChatEntryEntity(Long id, String timestamp, String author,
            String payload) {
        return ChatEntryEntity.builder().id(id).timestamp(timestamp).author(author).payload(payload)
                .userId(userId).chatId(chatId).build();
    }
}
