package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatUploadService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class ChatUploadController {
    
    private final ChatUploadService chatUploadService;
    private final UserService userService;
    
    /**
     * Upload a text chat file
     */
    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> uploadTextFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("Cannot get original filename"));
        }
        
        if (!originalFilename.toLowerCase().endsWith(".txt")) {
            return ResponseEntity.badRequest().body(createErrorResponse("Only TXT files are allowed"));
        }
        
        return processUpload(file, "text");
    }
    
    /**
     * Upload a ZIP file containing chat and multimedia files
     */
    @PostMapping("/zip")
    public ResponseEntity<Map<String, Object>> uploadZipFile(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "async", defaultValue = "false") boolean async) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("Cannot get original filename"));
        }
        
        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(createErrorResponse("Only ZIP files are allowed"));
        }
        
        // Check file size for async processing recommendation
        long fileSize = file.getSize();
        long largeFileThreshold = 50 * 1024 * 1024 * 1024; // 50MB
        
        if (fileSize > largeFileThreshold && !async) {
            Map<String, Object> response = new HashMap<>();
            response.put("warning", "Large file detected. Consider using async=true for better performance.");
            response.put("fileSize", fileSize);
            response.put("recommendedAsync", true);
            log.warn("Large ZIP file detected: {} bytes. User should consider async processing.", fileSize);
        }
        
        return processUpload(file, "zip");
    }
    
    /**
     * Generic upload method that determines file type automatically
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> uploadChatFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("Cannot get original filename"));
        }
        
        String lowerFilename = originalFilename.toLowerCase();
        if (lowerFilename.endsWith(".txt")) {
            return processUpload(file, "text");
        } else if (lowerFilename.endsWith(".zip")) {
            return processUpload(file, "zip");
        } else {
            return ResponseEntity.badRequest().body(createErrorResponse("Only TXT and ZIP files are allowed"));
        }
    }
    
    private ResponseEntity<Map<String, Object>> processUpload(MultipartFile file, String fileType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return ResponseEntity.status(401).body(createErrorResponse("Authentication required"));
        }
        
        String username = authentication.getName();
        if (username == null || username.isEmpty()) {
            return ResponseEntity.status(401).body(createErrorResponse("Invalid authentication"));
        }
        
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("User not found"));
        }
        
        var user = userOpt.get();
        long startTime = System.currentTimeMillis();
        
        try {
            ChatUploadService.UploadResult result;
            
            if ("text".equals(fileType)) {
                result = chatUploadService.uploadTextFile(file, user.getId());
            } else {
                result = chatUploadService.uploadZipFile(file, user.getId());
            }
            
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("chatId", result.getChatId());
            response.put("originalFileName", result.getOriginalFileName());
            response.put("fileType", result.getFileType());
            response.put("totalEntries", result.getTotalEntries());
            response.put("totalAttachments", result.getTotalAttachments());
            response.put("elapsedTime", elapsedTime);
            response.put("username", username);
            response.put("userId", user.getId());
            
            if (result.getExtractedFiles() != null) {
                response.put("extractedFiles", result.getExtractedFiles());
            }
            
            if (result.getErrorMessage() != null) {
                response.put("errorMessage", result.getErrorMessage());
            }
            
            log.info("Upload completed for user: {} chat: {} in {} ms", username, result.getChatId(), elapsedTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            
            log.error("Upload failed for user: {} after {} ms", username, elapsedTime, e);
            
            Map<String, Object> errorResponse = createErrorResponse("Upload failed: " + e.getMessage());
            errorResponse.put("elapsedTime", elapsedTime);
            errorResponse.put("username", username);
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorMessage", message);
        return response;
    }
} 