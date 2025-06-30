package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for tests
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void uploadChat_shouldStreamResponse() throws Exception {
        // Prepare test data
        String testContent = "Test chat content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat.txt",
                "text/plain",
                testContent.getBytes()
        );

        // Use a fixed timestamp for predictable test output
        LocalDateTime fixedTime = LocalDateTime.of(2025, 6, 30, 15, 2, 16, 809875000);
        doAnswer(invocation -> {
            Consumer<ChatEntry> consumer = invocation.getArgument(1);
            consumer.accept(ChatEntry.builder()
                    .timestamp("6/21/24, 7:19 AM")
                    .payload("Test Entry")
                    .build());
            return null;
        }).when(chatService).streamChatFile(any(InputStream.class), any(Consumer.class));

        mockMvc.perform(multipart("/api/chat/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("ChatEntry(timestamp=6/21/24, 7:19 AM, author=null, payload=Test Entry, fileName=null)\n"));
    }

    @Test
    void uploadChat_shouldHandleEmptyFile() throws Exception {
        // Prepare empty file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        // Perform request and verify
        mockMvc.perform(multipart("/api/chat/upload")
                        .file(file))
                .andExpect(status().isOk());
    }

    @Test
    void uploadChat_withoutFile_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/chat/upload"))
                .andExpect(status().isBadRequest());
    }
}