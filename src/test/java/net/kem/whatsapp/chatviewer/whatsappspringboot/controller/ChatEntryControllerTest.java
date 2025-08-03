package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;

@ExtendWith(MockitoExtension.class)
class ChatEntryControllerTest {

        @Mock
        private ChatEntryService chatEntryService;

        @Mock
        private UserService userService;

        @Mock
        private Authentication authentication;

        @Mock
        private SecurityContext securityContext;

        @InjectMocks
        private ChatEntryController chatEntryController;

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;

        private ChatEntryEntity testChatEntryEntity;
        private final Long userId = 1L;
        private final String username = "testuser";

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(chatEntryController).build();
                objectMapper = new ObjectMapper();

                testChatEntryEntity = ChatEntryEntity.builder()
                                .id(1L)
                                .author("John Doe")
                                .payload("Hello, world!")
                                .fileName(null)
                                .type(ChatEntry.Type.TEXT)
                                .localDateTime(LocalDateTime.of(2023, 12, 25, 14, 30))
                                .userId(userId)
                                .chatId("test_chat")
                                .build();

                // Mock authentication context with lenient stubbing
                lenient().when(authentication.getName()).thenReturn(username);
                lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
                SecurityContextHolder.setContext(securityContext);

                // Mock user service with lenient stubbing
                User testUser = User.builder().id(userId).username(username).build();
                lenient().when(userService.findByUsername(username)).thenReturn(Optional.of(testUser));
        }

        @Test
        void getChatEntry_ShouldReturnChatEntry_WhenExists() throws Exception {
                // Given
                when(chatEntryService.findById(1L, userId))
                                .thenReturn(Optional.of(testChatEntryEntity));

                // When & Then
                mockMvc.perform(get("/api/chat-entries/1")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.author").value("John Doe"))
                                .andExpect(jsonPath("$.payload").value("Hello, world!"));

                verify(chatEntryService).findById(1L, userId);
        }

        @Test
        void getChatEntry_ShouldReturn404_WhenNotExists() throws Exception {
                // Given
                when(chatEntryService.findById(999L, userId)).thenReturn(Optional.empty());

                // When & Then
                mockMvc.perform(get("/api/chat-entries/999")).andExpect(status().isNotFound());

                verify(chatEntryService).findById(999L, userId);
        }

        @Test
        void getAllChatEntries_ShouldReturnPagedResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                when(chatEntryService.findByUserId(userId, 0, 20)).thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries").param("page", "0").param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].id").value(1))
                                .andExpect(jsonPath("$.totalElements").value(1));

                verify(chatEntryService).findByUserId(userId, 0, 20);
        }

        @Test
        void searchChatEntries_ShouldReturnPagedResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                when(chatEntryService.searchChatEntries(anyLong(), anyString(), any(), any(), any(),
                                any(), any(), anyInt(), anyInt())).thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search").param("author", "John Doe")
                                .param("type", "TEXT").param("startDate", "2023-12-25T00:00:00")
                                .param("endDate", "2023-12-25T23:59:59")
                                .param("hasAttachment", "false")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].author").value("John Doe"));

                verify(chatEntryService).searchChatEntries(eq(userId), eq("John Doe"),
                                eq(ChatEntry.Type.TEXT), any(), any(), eq(false), any(), eq(0),
                                eq(20));
        }

        @Test
        void searchChatEntries_WithChatIds_ShouldReturnFilteredResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                List<String> chatIds = Arrays.asList("chat1_123_abc", "chat2_456_def");
                when(chatEntryService.searchChatEntries(anyLong(), anyString(), any(), any(), any(),
                                any(), eq(chatIds), anyInt(), anyInt())).thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search").param("author", "John Doe")
                                .param("chatIds", "chat1_123_abc", "chat2_456_def"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].author").value("John Doe"));

                verify(chatEntryService).searchChatEntries(eq(userId), eq("John Doe"), any(), any(),
                                any(), any(), eq(chatIds), eq(0), eq(20));
        }

        @Test
        void searchByKeyword_ShouldReturnPagedResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                when(chatEntryService.searchByKeyword(eq(userId), eq("Hello"), any(), eq(0), eq(20)))
                                .thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search/keyword").param("keyword", "Hello"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].payload").value("Hello, world!"));

                verify(chatEntryService).searchByKeyword(eq(userId), eq("Hello"), any(), eq(0), eq(20));
        }

        @Test
        void searchByKeyword_WithChatIds_ShouldReturnFilteredResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                List<String> chatIds = Arrays.asList("chat1_123_abc");
                when(chatEntryService.searchByKeyword(eq(userId), eq("Hello"), eq(chatIds), eq(0), eq(20)))
                                .thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search/keyword").param("keyword", "Hello")
                                .param("chatIds", "chat1_123_abc")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].payload").value("Hello, world!"));

                verify(chatEntryService).searchByKeyword(eq(userId), eq("Hello"), eq(chatIds), eq(0), eq(20));
        }

        @Test
        void advancedSearch_ShouldReturnPagedResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                when(chatEntryService.advancedSearch(anyLong(), anyString(), anyString(), any(),
                                any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search/advanced").param("keyword", "Hello")
                                .param("author", "John Doe").param("type", "TEXT"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].author").value("John Doe"));

                verify(chatEntryService).advancedSearch(eq(userId), eq("Hello"), eq("John Doe"),
                                eq(ChatEntry.Type.TEXT), any(), any(), any(), eq(0), eq(20));
        }

        @Test
        void advancedSearch_WithChatIds_ShouldReturnFilteredResults() throws Exception {
                // Given
                Page<ChatEntryEntity> page = new PageImpl<>(Arrays.asList(testChatEntryEntity),
                                PageRequest.of(0, 20), 1);
                List<String> chatIds = Arrays.asList("chat1_123_abc", "chat2_456_def");
                when(chatEntryService.advancedSearch(anyLong(), anyString(), anyString(), any(),
                                any(), any(), eq(chatIds), anyInt(), anyInt())).thenReturn(page);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/search/advanced").param("keyword", "Hello")
                                .param("author", "John Doe")
                                .param("chatIds", "chat1_123_abc", "chat2_456_def"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].author").value("John Doe"));

                verify(chatEntryService).advancedSearch(eq(userId), eq("Hello"), eq("John Doe"),
                                any(), any(), any(), eq(chatIds), eq(0), eq(20));
        }

        @Test
        void findByAuthor_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                when(chatEntryService.findByAuthor(userId, "John Doe")).thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/author/John Doe")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].author").value("John Doe"));

                verify(chatEntryService).findByAuthor(userId, "John Doe");
        }

        @Test
        void findByType_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                when(chatEntryService.findByType(userId, ChatEntry.Type.TEXT)).thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/type/TEXT")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].type").value("TEXT"));

                verify(chatEntryService).findByType(userId, ChatEntry.Type.TEXT);
        }

        @Test
        void findByDateRange_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                LocalDateTime startDate = LocalDateTime.of(2023, 12, 25, 0, 0);
                LocalDateTime endDate = LocalDateTime.of(2023, 12, 25, 23, 59, 59);
                when(chatEntryService.findByDateRange(userId, startDate, endDate)).thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/date-range")
                                .param("start", "2023-12-25T00:00:00")
                                .param("end", "2023-12-25T23:59:59")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(1));

                verify(chatEntryService).findByDateRange(userId, startDate, endDate);
        }

        @Test
        void findByAuthorAndType_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                when(chatEntryService.findByAuthorAndType(userId, "John Doe", ChatEntry.Type.TEXT))
                                .thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/author/John Doe/type/TEXT"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].author").value("John Doe"))
                                .andExpect(jsonPath("$[0].type").value("TEXT"));

                verify(chatEntryService).findByAuthorAndType(userId, "John Doe", ChatEntry.Type.TEXT);
        }

        @Test
        void findByAuthorAndDateRange_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                LocalDateTime startDate = LocalDateTime.of(2023, 12, 25, 0, 0);
                LocalDateTime endDate = LocalDateTime.of(2023, 12, 25, 23, 59, 59);
                when(chatEntryService.findByAuthorAndDateRange(userId, "John Doe", startDate, endDate))
                                .thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/author/John Doe/date-range")
                                .param("start", "2023-12-25T00:00:00")
                                .param("end", "2023-12-25T23:59:59")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].author").value("John Doe"));

                verify(chatEntryService).findByAuthorAndDateRange(userId, "John Doe", startDate, endDate);
        }

        @Test
        void findByTypeAndDateRange_ShouldReturnList() throws Exception {
                // Given
                List<ChatEntryEntity> entries = Arrays.asList(testChatEntryEntity);
                LocalDateTime startDate = LocalDateTime.of(2023, 12, 25, 0, 0);
                LocalDateTime endDate = LocalDateTime.of(2023, 12, 25, 23, 59, 59);
                when(chatEntryService.findByTypeAndDateRange(userId, ChatEntry.Type.TEXT, startDate, endDate))
                                .thenReturn(entries);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/type/TEXT/date-range")
                                .param("start", "2023-12-25T00:00:00")
                                .param("end", "2023-12-25T23:59:59")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].type").value("TEXT"));

                verify(chatEntryService).findByTypeAndDateRange(userId, ChatEntry.Type.TEXT, startDate, endDate);
        }

        @Test
        void countByAuthor_ShouldReturnCount() throws Exception {
                // Given
                when(chatEntryService.countByAuthor(userId, "John Doe")).thenReturn(5L);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/stats/author/John Doe"))
                                .andExpect(status().isOk()).andExpect(content().string("5"));

                verify(chatEntryService).countByAuthor(userId, "John Doe");
        }

        @Test
        void countByType_ShouldReturnCount() throws Exception {
                // Given
                when(chatEntryService.countByType(userId, ChatEntry.Type.TEXT)).thenReturn(10L);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/stats/type/TEXT")).andExpect(status().isOk())
                                .andExpect(content().string("10"));

                verify(chatEntryService).countByType(userId, ChatEntry.Type.TEXT);
        }

        @Test
        void countByDateRange_ShouldReturnCount() throws Exception {
                // Given
                when(chatEntryService.countByDateRange(anyLong(), any(), any())).thenReturn(3L);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/stats/date-range")
                                .param("start", "2023-12-25T00:00:00")
                                .param("end", "2023-12-25T23:59:59")).andExpect(status().isOk())
                                .andExpect(content().string("3"));

                verify(chatEntryService).countByDateRange(anyLong(), any(), any());
        }

        @Test
        void deleteChatEntry_ShouldReturnOk() throws Exception {
                // Given
                doNothing().when(chatEntryService).deleteById(1L);

                // When & Then
                mockMvc.perform(delete("/api/chat-entries/1")).andExpect(status().isOk());

                verify(chatEntryService).deleteById(1L);
        }

        @Test
        void getUserChats_ShouldReturnUserChatIds() throws Exception {
                // Given
                List<String> chatIds = Arrays.asList("chat1_123_abc", "chat2_456_def");
                when(chatEntryService.getChatIdsForUser(userId)).thenReturn(chatIds);

                // When & Then
                mockMvc.perform(get("/api/chat-entries/chats")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value("chat1_123_abc"))
                                .andExpect(jsonPath("$[1]").value("chat2_456_def"));

                verify(chatEntryService).getChatIdsForUser(userId);
        }
}
