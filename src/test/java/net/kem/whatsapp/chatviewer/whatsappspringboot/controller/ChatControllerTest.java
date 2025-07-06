package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntry;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for tests
@TestPropertySource(properties = {
    "spring.mvc.async.request-timeout=180000",
    "server.servlet.encoding.charset=UTF-8",
    "server.servlet.encoding.force=true"
})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ChatService chatService;

    private MockMvc createMockMvcWithUtf8() {
        return MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void uploadChat_shouldStreamResponse() throws Exception {
        String testContent = "Test chat content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat.txt",
                "text/plain",
                testContent.getBytes()
        );

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatEntry> consumer = (Consumer<ChatEntry>) invocation.getArgument(1, Consumer.class);
            consumer.accept(ChatEntry.builder()
                    .timestamp("6/21/24, 7:19 AM")
                    .payload("Test Entry")
                    .build());
            return null;
        }).when(chatService).streamChatFile(any(InputStream.class), any(Consumer.class));

        // 1. Perform the request and check async started
        var mvcResult = mockMvc.perform(multipart("/api/chat/upload").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. Dispatch the async result and assert on the response
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_NDJSON))
                .andExpect(content().string(containsString(
                        """
                        {"timestamp":"6/21/24, 7:19 AM","payload":"Test Entry"}
                        """)));
    }

    @Test
    void uploadChat_shouldStreamResponseX() throws Exception {
        // Prepare test data
        String testContent = "Test chat content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat.txt",
                "text/plain",
                testContent.getBytes()
        );

        // Use a fixed timestamp for predictable test output
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatEntry> consumer = (Consumer<ChatEntry>) invocation.getArgument(1, Consumer.class);
            consumer.accept(ChatEntry.builder()
                    .timestamp("6/21/24, 7:19")
                    .payload("Test Entry")
                    .build());
            return null;
        }).when(chatService).streamChatFile(any(InputStream.class), any(Consumer.class));

        // 1. Perform the request and check async started
        MockMvc mockMvcUtf8 = createMockMvcWithUtf8();
        var mvcResult = mockMvcUtf8.perform(multipart("/api/chat/uploadX").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. Dispatch the async result and assert on the response
        mockMvcUtf8.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "ChatEntry(timestamp=6/21/24, 7:19, author=null, payload=Test Entry, fileName=null)")));
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