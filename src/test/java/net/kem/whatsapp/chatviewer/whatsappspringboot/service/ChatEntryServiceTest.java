package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;

@ExtendWith(MockitoExtension.class)
class ChatEntryServiceTest {

    @Mock
    private ChatEntryRepository chatEntryRepository;

    @InjectMocks
    private ChatEntryService chatEntryService;

    private final Long userId = 1L;
    private final String chatId = "chat_abc";

    private ChatEntry testChatEntry;
    private ChatEntryEntity testChatEntryEntity;

    @BeforeEach
    void setUp() {
        testChatEntry = ChatEntry.builder().timestamp("12/25/23, 14:30").author("John Doe")
                .payload("Hello, world!").fileName(null).build();

        testChatEntryEntity = ChatEntryEntity.builder().id(1L).timestamp("12/25/23, 14:30")
                .author("John Doe").payload("Hello, world!").fileName(null)
                .type(ChatEntry.Type.TEXT).localDateTime(LocalDateTime.of(2023, 12, 25, 14, 30))
                .userId(userId).chatId(chatId).build();
    }

    @Test
    void saveChatEntry_ShouldSaveAndReturnEntity() {
        // Given
        when(chatEntryRepository.save(any(ChatEntryEntity.class))).thenReturn(testChatEntryEntity);

        // When
        ChatEntryEntity result = chatEntryService.saveChatEntry(testChatEntry, userId, chatId);

        // Then
        assertNotNull(result);
        assertEquals(testChatEntryEntity.getId(), result.getId());
        assertEquals(testChatEntryEntity.getAuthor(), result.getAuthor());
        verify(chatEntryRepository).save(any(ChatEntryEntity.class));
    }

    @Test
    void saveChatEntries_ShouldSaveMultipleEntries() {
        // Given
        List<ChatEntry> chatEntries = Arrays.asList(testChatEntry, testChatEntry);
        List<ChatEntryEntity> entities = Arrays.asList(testChatEntryEntity, testChatEntryEntity);
        when(chatEntryRepository.saveAll(anyList())).thenReturn(entities);

        // When
        List<ChatEntryEntity> result =
                chatEntryService.saveChatEntries(chatEntries, userId, chatId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(chatEntryRepository).saveAll(anyList());
    }

    @Test
    void findById_ShouldReturnEntity_WhenExists() {
        // Given
        when(chatEntryRepository.findById(1L)).thenReturn(Optional.of(testChatEntryEntity));

        // When
        Optional<ChatEntryEntity> result = chatEntryService.findById(1L, userId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testChatEntryEntity.getId(), result.get().getId());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotExists() {
        // Given
        when(chatEntryRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<ChatEntryEntity> result = chatEntryService.findById(999L, userId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void searchChatEntries_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page =
                new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findByUserId(userId, pageable)).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.searchChatEntries(userId, "John Doe",
                ChatEntry.Type.TEXT, LocalDateTime.now(), LocalDateTime.now(), true, null, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findByUserId(userId, pageable);
    }

    @Test
    void searchByKeyword_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page =
                new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.searchByUserIdAndKeyword(userId, "Hello", pageable))
                .thenReturn(page);

        // When
        Page<ChatEntryEntity> result =
                chatEntryService.searchByKeyword(userId, "Hello", null, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).searchByUserIdAndKeyword(userId, "Hello", pageable);
    }

    @Test
    void searchByKeyword_ShouldSanitizeSensitiveData() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        ChatEntryEntity sensitiveEntry = ChatEntryEntity.builder().id(1L).timestamp("1/8/24, 14:48")
                .author("Freddie Kruger")
                .payload(
                        "monitorRole NoneNone join-key: 39ceb47b6724c6f9c3588f1f9f6b3d33 master-key: 39ceb47b6724c6f9c3588f1f9 f6b3d33")
                .fileName(null).type(ChatEntry.Type.TEXT)
                .localDateTime(LocalDateTime.of(2024, 1, 8, 14, 48)).userId(userId).chatId(chatId)
                .build();

        Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(sensitiveEntry), pageable, 1);
        when(chatEntryRepository.searchByUserIdAndKeyword(userId, "role", pageable))
                .thenReturn(page);

        // When
        Page<ChatEntryEntity> result =
                chatEntryService.searchByKeyword(userId, "role", null, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        ChatEntryEntity sanitizedEntry = result.getContent().get(0);
        assertTrue(sanitizedEntry.getPayload().contains("join-key: [REDACTED]"));
        assertTrue(sanitizedEntry.getPayload().contains("master-key: [REDACTED]"));
        assertFalse(sanitizedEntry.getPayload().contains("39ceb47b6724c6f9c3588f1f9f6b3d33"));

        verify(chatEntryRepository).searchByUserIdAndKeyword(userId, "role", pageable);
    }

    @Test
    void advancedSearch_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page =
                new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findByUserId(userId, pageable)).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.advancedSearch(userId, "Hello", "John Doe",
                ChatEntry.Type.TEXT, LocalDateTime.now(), LocalDateTime.now(), null, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findByUserId(userId, pageable);
    }

    @Test
    void findByAuthor_ShouldReturnList() {
        // Given
        when(chatEntryRepository.findByAuthor("John Doe"))
                .thenReturn(Arrays.asList(testChatEntryEntity));

        // When
        List<ChatEntryEntity> result = chatEntryService.findByAuthor("John Doe");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).getAuthor());
    }

    @Test
    void findByType_ShouldReturnList() {
        // Given
        when(chatEntryRepository.findByType(ChatEntry.Type.TEXT))
                .thenReturn(Arrays.asList(testChatEntryEntity));

        // When
        List<ChatEntryEntity> result = chatEntryService.findByType(ChatEntry.Type.TEXT);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ChatEntry.Type.TEXT, result.get(0).getType());
    }

    @Test
    void findByDateRange_ShouldReturnList() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        when(chatEntryRepository.findByLocalDateTimeBetween(start, end))
                .thenReturn(Arrays.asList(testChatEntryEntity));

        // When
        List<ChatEntryEntity> result = chatEntryService.findByDateRange(start, end);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(chatEntryRepository).findByLocalDateTimeBetween(start, end);
    }

    @Test
    void deleteById_ShouldDeleteEntity() {
        // Given
        doNothing().when(chatEntryRepository).deleteById(1L);

        // When
        chatEntryService.deleteById(1L);

        // Then
        verify(chatEntryRepository).deleteById(1L);
    }

    @Test
    void findAll_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page =
                new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findByUserId(userId, pageable)).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.findAll(0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findByUserId(userId, pageable);
    }

    @Test
    void countByAuthor_ShouldReturnCount() {
        // Given
        when(chatEntryRepository.countByUserIdAndAuthor(userId, "John Doe")).thenReturn(5L);

        // When
        long result = chatEntryService.countByAuthor(userId, "John Doe");

        // Then
        assertEquals(5L, result);
        verify(chatEntryRepository).countByUserIdAndAuthor(userId, "John Doe");
    }

    @Test
    void countByType_ShouldReturnCount() {
        // Given
        when(chatEntryRepository.countByUserIdAndType(userId, ChatEntry.Type.TEXT)).thenReturn(10L);

        // When
        long result = chatEntryService.countByType(userId, ChatEntry.Type.TEXT);

        // Then
        assertEquals(10L, result);
        verify(chatEntryRepository).countByUserIdAndType(userId, ChatEntry.Type.TEXT);
    }

    @Test
    void countByDateRange_ShouldReturnCount() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        when(chatEntryRepository.countByUserIdAndLocalDateTimeBetween(userId, start, end))
                .thenReturn(3L);

        // When
        long result = chatEntryService.countByDateRange(userId, start, end);

        // Then
        assertEquals(3L, result);
        verify(chatEntryRepository).countByUserIdAndLocalDateTimeBetween(userId, start, end);
    }
}
