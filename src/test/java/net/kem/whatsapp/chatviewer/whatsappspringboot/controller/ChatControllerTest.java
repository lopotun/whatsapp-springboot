package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatUploadService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatUploadController.class)
@AutoConfigureMockMvc
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
    private ChatUploadService chatUploadService;

    @MockitoBean
    private UserService userService;

    private MockMvc createMockMvcWithUtf8() {
        return MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void uploadTextFile_shouldReturnSuccess() throws Exception {
        String testContent = "12/25/23, 14:30 - John Doe: Hello, world!";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat.txt",
                "text/plain",
                testContent.getBytes()
        );

        ChatUploadService.UploadResult result = ChatUploadService.UploadResult.builder()
                .chatId("chat_123_abc")
                .originalFileName("chat.txt")
                .fileType("TXT")
                .totalEntries(1)
                .totalAttachments(0)
                .success(true)
                .build();

        when(chatUploadService.uploadTextFile(any(), any())).thenReturn(result);
        when(userService.findByUsername(any())).thenReturn(java.util.Optional.of(
                net.kem.whatsapp.chatviewer.whatsappspringboot.model.User.builder()
                        .id(1L)
                        .username("testuser")
                        .build()
        ));

        mockMvc.perform(multipart("/api/upload/text")
                        .file(file)
                        .with(user("testuser"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("chat_123_abc")));
    }

    @Test
    void uploadZipFile_shouldReturnSuccess() throws Exception {
        byte[] zipContent = createTestZipContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.zip",
                "application/zip",
                zipContent
        );

        ChatUploadService.UploadResult result = ChatUploadService.UploadResult.builder()
                .chatId("zip_456_def")
                .originalFileName("test.zip")
                .fileType("ZIP")
                .totalEntries(5)
                .totalAttachments(3)
                .extractedFiles(List.of("file1.jpg", "file2.mp4"))
                .success(true)
                .build();

        when(chatUploadService.uploadZipFile(any(), any())).thenReturn(result);
        when(userService.findByUsername(any())).thenReturn(java.util.Optional.of(
                net.kem.whatsapp.chatviewer.whatsappspringboot.model.User.builder()
                        .id(1L)
                        .username("testuser")
                        .build()
        ));

        mockMvc.perform(multipart("/api/upload/zip")
                        .file(file)
                        .with(user("testuser"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("zip_456_def")));
    }

    @Test
    void upload_withoutFile_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/upload/text")
                        .with(user("testuser"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadZipFile_withNonZipFile_shouldReturnBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not a zip file".getBytes()
        );

        mockMvc.perform(multipart("/api/upload/zip")
                        .file(file)
                        .with(user("testuser"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private byte[] createTestZipContent() {
        // This is a minimal valid ZIP file structure for testing
        // In a real test, you might want to create an actual ZIP file with test content
        return new byte[] {
            0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x50, 0x4B, 0x01, 0x02, 0x14, 0x00, 0x14, 0x00, 0x00, 0x00,
            0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x50, 0x4B, 0x05, 0x06,
            0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x2C, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
    }
}