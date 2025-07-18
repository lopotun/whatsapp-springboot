package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/upload")
    public ResponseEntity<StreamingResponseBody> uploadChat(@RequestParam("file") MultipartFile file) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        StreamingResponseBody responseBody = outputStream -> {
            try {
                chatService.streamChatFile(file.getInputStream(), entry -> {
                    try {
                        objectMapper.writeValue(outputStream, entry);
                        outputStream.write('\n');
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Error writing to output stream", e);
                    }
                }, new HashMap<>()); // Empty map for single file upload
            } catch (IOException e) {
                throw new RuntimeException("Error processing file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(responseBody);
    }

    @PostMapping("/uploadX")
    public ResponseEntity<StreamingResponseBody> uploadChatX(@RequestParam("file") MultipartFile file) {
        StreamingResponseBody responseBody = outputStream -> {
            try {
                chatService.streamChatFile(file.getInputStream(), entry -> {
                    try {
                        outputStream.write((entry.toString() + "\n").getBytes());
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Error writing to output stream", e);
                    }
                }, new HashMap<>()); // Empty map for single file upload
            } catch (IOException e) {
                throw new RuntimeException("Error processing file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }

    @PostMapping("/upload-zip")
    public ResponseEntity<String> uploadZipFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body("Cannot get chat file name");
        }
        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body("Only ZIP files are allowed");
        }

        long startTime = System.currentTimeMillis();
        
        try {
            List<String> extractedFiles = chatService.processZipFile(file.getInputStream(), entry -> {
                // Process entry silently - no streaming to output
            });
            
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            String result = String.format("Upload completed in %d ms. Extracted %d multimedia files.", 
                elapsedTime, extractedFiles.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            String errorResult = String.format("Upload failed after %d ms. Error: %s", 
                elapsedTime, e.getMessage());
            
            return ResponseEntity.status(500).body(errorResult);
        }
    }
}