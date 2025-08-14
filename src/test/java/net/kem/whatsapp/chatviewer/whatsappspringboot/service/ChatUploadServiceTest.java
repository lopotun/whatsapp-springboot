package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        private final String chatId = "user1_testchat_123456_abc123";

        @BeforeEach
        void setUp() {
                // No setup needed for these tests
        }

        @Test
        void uploadTextFile_IncrementalUpdate_ShouldKeepExistingAndAddNewEntries()
                        throws Exception {
                // Given
                String firstContent = "12/25/23, 14:30 - John Doe: Hello, world!\n"
                                + "12/25/23, 14:31 - John Doe: How are you?\n"
                                + "12/25/23, 14:32 - John Doe: How are you?\n"
                                + "12/25/23, 14:33 - Jane Doe: I'm good!";
                MockMultipartFile firstFile = new MockMultipartFile("file", "testchat.txt",
                                "text/plain", firstContent.getBytes());

                String secondContent = "12/25/23, 14:30 - John Doe: Hello, world!\n"
                                + "12/25/23, 14:32 - John Doe: How are you?\n"
                                + "12/25/23, 14:33 - Jane Doe: I'm good!\n"
                                + "12/25/23, 14:34 - John Doe: Great!";
                MockMultipartFile secondFile = new MockMultipartFile("file", "testchat.txt",
                                "text/plain", secondContent.getBytes());

                // Mock that chat doesn't exist initially, then exists for re-upload
                when(chatService.chatExists(anyLong(), anyString())).thenReturn(false)
                                .thenReturn(true);



                // Mock existing entries for incremental update (needed for the second upload)
                List<ChatEntryEntity> existingEntries = Arrays.asList(
                                createTestChatEntryEntity(1L, "John Doe", "Hello, world!"),
                                createTestChatEntryEntity(2L, "John Doe", "How are you?"),
                                createTestChatEntryEntity(3L, "John Doe", "How are you?"),
                                createTestChatEntryEntity(4L, "Jane Doe", "I'm good!"));

                when(chatEntryService.findByUserIdAndChatId(anyLong(), anyString()))
                                .thenReturn(existingEntries);

                // Mock the first save operation
                List<ChatEntryEntity> firstSavedEntries = Arrays.asList(
                                createTestChatEntryEntity(1L, "John Doe", "Hello, world!"),
                                createTestChatEntryEntity(2L, "John Doe", "How are you?"),
                                createTestChatEntryEntity(3L, "John Doe", "How are you?"),
                                createTestChatEntryEntity(4L, "Jane Doe", "I'm good!"));
                when(chatEntryService.saveChatEntries(anyList(), eq(userId), anyString()))
                                .thenReturn(firstSavedEntries);

                // When - First upload
                ChatUploadService.UploadResult firstResult =
                                chatUploadService.uploadTextFile(firstFile, userId);

                // Then - First upload should result in 4 entries
                assertNotNull(firstResult);
                assertTrue(firstResult.isSuccess());
                assertEquals(4, firstResult.getTotalEntries());



                // When - Second upload (incremental update)
                ChatUploadService.UploadResult secondResult =
                                chatUploadService.uploadTextFile(secondFile, userId);

                // Then - Second upload should result in A, C, D, E (B removed, E added)
                assertNotNull(secondResult);
                assertTrue(secondResult.isSuccess());
                assertEquals(4, secondResult.getTotalEntries());

                // Verify that incremental update was performed
                verify(chatService, atLeastOnce()).chatExists(anyLong(), anyString());
                verify(chatEntryService, times(1)).findByUserIdAndChatId(anyLong(), anyString());
                verify(chatEntryService, times(3)).deleteById(anyLong(), anyLong());

                // Verify that kept entries and new entries were saved
                verify(chatEntryService, times(1)).saveChatEntries(anyList(), eq(userId),
                                anyString());
        }

        @Test
        void uploadTextFile_NewUpload_ShouldNotPerformIncrementalUpdate() throws Exception {
                // Given
                String testContent = "12/25/23, 14:30 - John Doe: Hello, world!";
                MockMultipartFile file = new MockMultipartFile("file", "newchat.txt", "text/plain",
                                testContent.getBytes());

                // Mock that chat does not exist (new upload scenario)
                when(chatService.chatExists(anyLong(), anyString())).thenReturn(false);

                // Mock the save operation
                List<ChatEntryEntity> savedEntries = Arrays
                                .asList(createTestChatEntryEntity(1L, "John Doe", "Hello, world!"));
                when(chatEntryService.saveChatEntries(anyList(), eq(userId), anyString()))
                                .thenReturn(savedEntries);

                // When
                ChatUploadService.UploadResult result =
                                chatUploadService.uploadTextFile(file, userId);

                // Then
                assertNotNull(result);
                assertTrue(result.isSuccess());
                assertEquals(1, result.getTotalEntries());

                // Verify that no incremental update was performed for new upload
                verify(chatService, times(1)).chatExists(anyLong(), anyString());
                verify(chatEntryService, never()).findByUserIdAndChatId(anyLong(), anyString());
                verify(chatEntryService, never()).deleteByUserIdAndChatId(anyLong(), anyString());
                verify(chatEntryService).saveChatEntries(anyList(), eq(userId), anyString());
        }

        @Test
        void generateChatId_SameFilename_ShouldReturnSameChatId() {
                // Given
                String filename = "test_chat.txt";
                String expectedChatId = "user1_test_chat";

                // When
                String chatId1 = chatUploadService.generateChatId(filename, userId);
                String chatId2 = chatUploadService.generateChatId(filename, userId);

                // Then
                assertEquals(chatId1, chatId2);
                assertEquals(expectedChatId, chatId1);
        }

        @Test
        void generateChatId_DifferentFilename_ShouldReturnDifferentChatId() {
                // Given
                String filename1 = "chat1.txt";
                String filename2 = "chat2.txt";
                String expectedChatId1 = "user1_chat1";
                String expectedChatId2 = "user1_chat2";

                // When
                String chatId1 = chatUploadService.generateChatId(filename1, userId);
                String chatId2 = chatUploadService.generateChatId(filename2, userId);

                // Then
                assertNotEquals(chatId1, chatId2);
                assertEquals(expectedChatId1, chatId1);
                assertEquals(expectedChatId2, chatId2);
        }

        private ChatEntryEntity createTestChatEntryEntity(Long id, String author, String payload) {
                return ChatEntryEntity.builder().id(id).author(author).payload(payload)
                                .userId(userId).chatId(chatId)
                                .localDateTime(LocalDateTime.of(2023, 12, 25, 14, 30)).build();
        }
}
