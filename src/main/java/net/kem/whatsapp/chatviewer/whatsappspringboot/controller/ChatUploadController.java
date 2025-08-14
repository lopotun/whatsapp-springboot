package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatUploadService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    public ResponseEntity<Map<String, Object>> uploadTextFile(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Cannot get original filename"));
        }

        if (!originalFilename.toLowerCase().endsWith(".txt")) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Only TXT files are allowed"));
        }

        return processUpload(file, "text");
    }

    /**
     * Upload a ZIP file containing chat and multimedia files
     */
    @PostMapping("/zip")
    public ResponseEntity<Map<String, Object>> uploadZipFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "async", defaultValue = "true") boolean async) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Cannot get original filename"));
        }

        if (!originalFilename.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Only ZIP files are allowed"));
        }

        if (async) {
            // Start async processing and return upload ID immediately
            String uploadId = chatUploadService.startAsyncZipProcessing(file, getCurrentUserId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", uploadId);
            response.put("message", "ZIP upload started. Processing in background.");
            response.put("status", "processing");

            return ResponseEntity.ok(response);
        } else {
            // Process synchronously (for small files)
            return processUpload(file, "zip");
        }
    }

    /**
     * Monitor progress of ZIP processing
     */
    @GetMapping(value = "/progress/{uploadId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter monitorProgress(@PathVariable String uploadId) {
        log.info("SSE progress endpoint called for upload: {}", uploadId);
        SseEmitter emitter = chatUploadService.createProgressEmitter(uploadId);
        log.info("SSE emitter created for upload: {} - Emitter: {}", uploadId, emitter != null);
        return emitter;
    }

    @GetMapping(value = "/status/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable String uploadId) {
        log.info("Upload status endpoint called for upload: {}", uploadId);

        var progress = chatUploadService.getUploadProgress(uploadId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> status = new HashMap<>();
        status.put("uploadId", uploadId);
        status.put("progress", progress.getProgress());
        status.put("message", progress.getMessage());
        status.put("hasError", progress.getError() != null);
        status.put("error", progress.getError());
        status.put("hasResult", progress.getResult() != null);
        status.put("result", progress.getResult());

        return ResponseEntity.ok(status);
    }

    /**
     * Generic upload method that determines file type automatically
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> uploadChatFile(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("File is empty"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Cannot get original filename"));
        }

        String lowerFilename = originalFilename.toLowerCase();
        if (lowerFilename.endsWith(".txt")) {
            return processUpload(file, "text");
        } else if (lowerFilename.endsWith(".zip")) {
            return processUpload(file, "zip");
        } else {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Only TXT and ZIP files are allowed"));
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

            log.info("Upload completed for user: {} chat: {} in {} ms", username,
                    result.getChatId(), elapsedTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            log.error("Upload failed for user: {} after {} ms", username, elapsedTime, e);

            Map<String, Object> errorResponse =
                    createErrorResponse("Upload failed: " + e.getMessage());
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

    /**
     * Get current user ID from authentication
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();

        // Try to find user by username first (traditional auth)
        var userOpt = userService.findByUsername(principalName);

        // If not found, try to find by OAuth2 ID (OAuth2 auth)
        if (userOpt.isEmpty() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthToken.getName();
            userOpt = userService.findByOauthProviderAndOauthId(provider, oauthId);
        }

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        return userOpt.get().getId();
    }
}
