package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatEntryServiceTest {

    @Mock
    private ChatEntryRepository chatEntryRepository;

    @InjectMocks
    private ChatEntryService chatEntryService;

    private ChatEntry testChatEntry;
    private ChatEntryEntity testChatEntryEntity;

    @BeforeEach
    void setUp() {
        testChatEntry = ChatEntry.builder()
                .timestamp("12/25/23, 14:30")
                .author("John Doe")
                .payload("Hello, world!")
                .fileName(null)
                .build();

        testChatEntryEntity = ChatEntryEntity.builder()
                .id(1L)
                .timestamp("12/25/23, 14:30")
                .author("John Doe")
                .payload("Hello, world!")
                .fileName(null)
                .type(ChatEntry.Type.TEXT)
                .localDateTime(LocalDateTime.of(2023, 12, 25, 14, 30))
                .build();
    }

    @Test
    void saveChatEntry_ShouldSaveAndReturnEntity() {
        // Given
        when(chatEntryRepository.save(any(ChatEntryEntity.class))).thenReturn(testChatEntryEntity);

        // When
        ChatEntryEntity result = chatEntryService.saveChatEntry(testChatEntry);

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
        List<ChatEntryEntity> result = chatEntryService.saveChatEntries(chatEntries);

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
        Optional<ChatEntryEntity> result = chatEntryService.findById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testChatEntryEntity.getId(), result.get().getId());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotExists() {
        // Given
        when(chatEntryRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<ChatEntryEntity> result = chatEntryService.findById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void searchChatEntries_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.searchChatEntries(
                "John Doe", ChatEntry.Type.TEXT, LocalDateTime.now(), LocalDateTime.now(), true, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findAll(any(Pageable.class));
    }

    @Test
    void searchByKeyword_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.searchByKeyword(anyString(), any(Pageable.class))).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.searchByKeyword("Hello", 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).searchByKeyword("Hello", pageable);
    }

    @Test
    void advancedSearch_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.advancedSearch(
                "Hello", "John Doe", ChatEntry.Type.TEXT, LocalDateTime.now(), LocalDateTime.now(), 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findAll(any(Pageable.class));
    }

    @Test
    void findByAuthor_ShouldReturnList() {
        // Given
        when(chatEntryRepository.findByAuthor("John Doe")).thenReturn(Arrays.asList(testChatEntryEntity));

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
        when(chatEntryRepository.findByType(ChatEntry.Type.TEXT)).thenReturn(Arrays.asList(testChatEntryEntity));

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
    void updateAttachmentHash_ShouldUpdateEntity_WhenExists() {
        // Given
        when(chatEntryRepository.findById(1L)).thenReturn(Optional.of(testChatEntryEntity));
        when(chatEntryRepository.save(any(ChatEntryEntity.class))).thenReturn(testChatEntryEntity);

        // When
        chatEntryService.updateAttachmentHash(1L, "test-hash");

        // Then
        verify(chatEntryRepository).findById(1L);
        verify(chatEntryRepository).save(any(ChatEntryEntity.class));
    }

    @Test
    void updateAttachmentHash_ShouldNotUpdate_WhenNotExists() {
        // Given
        when(chatEntryRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        chatEntryService.updateAttachmentHash(999L, "test-hash");

        // Then
        verify(chatEntryRepository).findById(999L);
        verify(chatEntryRepository, never()).save(any(ChatEntryEntity.class));
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
        Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity), pageable, 1);
        when(chatEntryRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<ChatEntryEntity> result = chatEntryService.findAll(0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(chatEntryRepository).findAll(pageable);
    }

    @Test
    void countByAuthor_ShouldReturnCount() {
        // Given
        when(chatEntryRepository.countByAuthor("John Doe")).thenReturn(5L);

        // When
        long result = chatEntryService.countByAuthor("John Doe");

        // Then
        assertEquals(5L, result);
        verify(chatEntryRepository).countByAuthor("John Doe");
    }

    @Test
    void countByType_ShouldReturnCount() {
        // Given
        when(chatEntryRepository.countByType(ChatEntry.Type.TEXT)).thenReturn(10L);

        // When
        long result = chatEntryService.countByType(ChatEntry.Type.TEXT);

        // Then
        assertEquals(10L, result);
        verify(chatEntryRepository).countByType(ChatEntry.Type.TEXT);
    }

    @Test
    void countByDateRange_ShouldReturnCount() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        when(chatEntryRepository.countByLocalDateTimeBetween(start, end)).thenReturn(3L);

        // When
        long result = chatEntryService.countByDateRange(start, end);

        // Then
        assertEquals(3L, result);
        verify(chatEntryRepository).countByLocalDateTimeBetween(start, end);
    }
} 