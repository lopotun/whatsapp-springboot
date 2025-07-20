package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatUploadServiceTest {

    @Mock
    private ChatEntryService chatEntryService;

    @Mock
    private FileNamingService fileNamingService;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private ChatUploadService chatUploadService;

    private final Long userId = 1L;
    private final String filename = "WhatsApp Chat with John Doe.txt";

    @Test
    void generateChatId_ShouldReturnSameId_ForSameFilenameAndUser() {
        // Given - First upload
        String existingChatId = "whatsappchatwithjohndoetxt_1234567890_abc12345";
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList(existingChatId));

        // When - Generate chat ID for the same filename
        String result1 = chatUploadService.generateChatId(filename, userId);
        String result2 = chatUploadService.generateChatId(filename, userId);

        // Then - Both should return the same existing chat ID
        assertEquals(existingChatId, result1);
        assertEquals(existingChatId, result2);
        assertEquals(result1, result2);

        verify(chatEntryService, times(2)).getChatIdsForUser(userId);
    }

    @Test
    void generateChatId_ShouldReturnNewId_ForDifferentFilename() {
        // Given - User has existing chats
        String existingChatId = "whatsappchatwithjohndoetxt_1234567890_abc12345";
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList(existingChatId));

        // When - Generate chat ID for different filename
        String differentFilename = "WhatsApp Chat with Jane Smith.txt";
        String result = chatUploadService.generateChatId(differentFilename, userId);

        // Then - Should return a new chat ID
        assertNotEquals(existingChatId, result);
        assertTrue(result.startsWith("whatsappchatwithjanesmithtxt_"));

        verify(chatEntryService).getChatIdsForUser(userId);
    }

    @Test
    void generateChatId_ShouldReturnNewId_ForNewUser() {
        // Given - New user with no existing chats
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList());

        // When - Generate chat ID for new user
        String result = chatUploadService.generateChatId(filename, userId);

        // Then - Should return a new chat ID
        assertTrue(result.startsWith("whatsappchatwithjohndoetxt_"));
        assertTrue(result.contains("_")); // Should have timestamp and UUID

        verify(chatEntryService).getChatIdsForUser(userId);
    }

    @Test
    void generateChatId_ShouldHandleSpecialCharacters() {
        // Given - Filename with special characters
        String filenameWithSpecialChars = "WhatsApp Chat (Group) - 2024-01-15.txt";
        when(chatEntryService.getChatIdsForUser(userId))
                .thenReturn(Arrays.asList());

        // When - Generate chat ID
        String result = chatUploadService.generateChatId(filenameWithSpecialChars, userId);

        // Then - Should remove special characters and create valid chat ID
        assertTrue(result.startsWith("whatsappchatgroup20240115txt_"));
        assertFalse(result.contains("("));
        assertFalse(result.contains(")"));
        assertFalse(result.contains("-"));
        assertFalse(result.contains(" "));

        verify(chatEntryService).getChatIdsForUser(userId);
    }
} 