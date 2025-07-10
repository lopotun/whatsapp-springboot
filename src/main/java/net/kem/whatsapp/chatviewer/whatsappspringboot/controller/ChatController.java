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
                });
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
                });
            } catch (IOException e) {
                throw new RuntimeException("Error processing file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }

    @PostMapping("/upload-zip")
    public ResponseEntity<StreamingResponseBody> uploadZipFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> outputStream.write("File is empty".getBytes()));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> outputStream.write("Cannot get chat file name".getBytes()));
        }
        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> outputStream.write("Only ZIP files are allowed".getBytes()));
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        StreamingResponseBody responseBody = outputStream -> {
            try {
                List<String> extractedFiles = chatService.processZipFile(file.getInputStream(), entry -> {
                    try {
                        objectMapper.writeValue(outputStream, entry);
                        outputStream.write('\n');
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Error writing to output stream", e);
                    }
                });
                
                // Write summary of extracted files
                outputStream.write(("\nExtracted " + extractedFiles.size() + " multimedia files:\n").getBytes());
                for (String filePath : extractedFiles) {
                    outputStream.write((filePath + "\n").getBytes());
                }
                outputStream.flush();
                
            } catch (IOException e) {
                throw new RuntimeException("Error processing zip file", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(responseBody);
    }
}