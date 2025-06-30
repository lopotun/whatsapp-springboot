package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/upload")
    public ResponseEntity<StreamingResponseBody> uploadChat(@RequestParam("file") MultipartFile file) {
        StreamingResponseBody responseBody = outputStream -> {
            try {
                chatService.streamChatFile(file.getInputStream(), entry -> {
                    try {
                        outputStream.write((entry.toString() + "\n").getBytes());
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Error writing to output stream", e);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Error processing file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }
}