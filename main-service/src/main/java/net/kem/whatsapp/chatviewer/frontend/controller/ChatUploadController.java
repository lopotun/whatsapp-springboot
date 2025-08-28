package net.kem.whatsapp.chatviewer.frontend.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.frontend.service.ChatUploadService;
import net.kem.whatsapp.chatviewer.frontend.service.UserService;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class ChatUploadController {

    @Value("${app.zipper-service.base-url}")
    private String zipperServiceBaseUrl;

    @Value("${app.zipper-service.api-key}")
    private String zipperServiceApiKey;

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

        // Always use the new zipper service flow
        return processUpload(file, "zip");
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
        log.info("processUpload called with fileType: {}, file size: {} bytes", fileType,
                file.getSize());

        try {
            // Use the existing method that handles both traditional and OAuth2 users
            Long userId = getCurrentUserId();
            log.info("User ID resolved: {}", userId);

            long startTime = System.currentTimeMillis();

            ChatUploadService.UploadResult result;
            log.info("File type is: '{}', routing to appropriate processor", fileType);

            if ("text".equals(fileType)) {
                log.info("Processing as text file");
                result = chatUploadService.uploadTextFile(file, userId);
            } else {
                log.info("Processing as ZIP file, delegating to zipper service");
                // For ZIP files, delegate to zipper service
                result = processZipWithZipperService(file, userId);
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
            response.put("userId", userId);

            if (result.getExtractedFiles() != null) {
                response.put("extractedFiles", result.getExtractedFiles());
            }

            if (result.getErrorMessage() != null) {
                response.put("errorMessage", result.getErrorMessage());
            }

            log.info("Upload completed for user ID: {} chat: {} in {} ms", userId,
                    result.getChatId(), elapsedTime);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);

            Map<String, Object> errorResponse =
                    createErrorResponse("Upload failed: " + e.getMessage());

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
     * Process ZIP file using the zipper service
     */
    private ChatUploadService.UploadResult processZipWithZipperService(MultipartFile file,
            Long userId) {
        long startTime = System.currentTimeMillis();
        log.info("Starting ZIP processing delegation for user: {}, file size: {} bytes", userId,
                file.getSize());

        try {
            // Generate a unique job ID
            String jobId = "job_" + System.currentTimeMillis() + "_" + userId;
            String chatId = generateChatId(file.getOriginalFilename(), userId);
            log.info("Generated job ID: {} and chat ID: {}", jobId, chatId);

            // Store the file temporarily
            log.info("Creating temporary file for ZIP upload...");
            Path tempFile = Files.createTempFile("zipper_upload_", ".zip");
            log.info("Temporary file created: {}", tempFile);

            log.info("Transferring file content to temporary file...");
            file.transferTo(tempFile.toFile());
            log.info("File transfer completed in {} ms", System.currentTimeMillis() - startTime);

            // Create a job for the zipper service
            ZipProcessingJobRequest jobRequest = ZipProcessingJobRequest.builder().jobId(jobId)
                    .userId(userId).chatId(chatId).originalFilename(file.getOriginalFilename())
                    .filePath(tempFile.toString()).fileSize(file.getSize()).build();
            log.info("Job request created: {}", jobRequest);

            // Send job to zipper service with timeout
            String zipperServiceUrl = zipperServiceBaseUrl + "/api/v1/jobs";
            log.info("Sending job to zipper service: {}", zipperServiceUrl);

            // Create RestTemplate with timeout
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(
                    new org.springframework.http.client.SimpleClientHttpRequestFactory());
            ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate
                    .getRequestFactory()).setConnectTimeout(0); // TODO: revert to 10_000 ms
            ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate
                    .getRequestFactory()).setReadTimeout(0); // TODO: revert to 30_000 ms

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ZipProcessingJobRequest> entity = new HttpEntity<>(jobRequest, headers);
            log.info("Sending HTTP request to zipper service...");

            ResponseEntity<String> response =
                    restTemplate.postForEntity(zipperServiceUrl, entity, String.class);
            log.info("Zipper service response received: {} in {} ms", response.getStatusCode(),
                    System.currentTimeMillis() - startTime);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully submitted job {} to zipper service in {} ms", jobId,
                        System.currentTimeMillis() - startTime);

                // Return a result indicating the job was submitted
                return ChatUploadService.UploadResult.builder().success(true).chatId(chatId)
                        .originalFileName(file.getOriginalFilename()).fileType("zip")
                        // TODO: remove these counts or fetch real values from zipper service
                        .totalEntries(0) // Will be updated when zipper completes
                        .totalAttachments(0) // Will be updated when zipper completes
                        .build();
            } else {
                log.error("Failed to submit job to zipper service: {}", response.getStatusCode());
                throw new RuntimeException("Failed to submit job to zipper service");
            }

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("Error delegating ZIP processing to zipper service after {} ms: {}",
                    totalTime, e.getMessage(), e);
            throw new RuntimeException("Failed to process ZIP with zipper service after "
                    + totalTime + " ms: " + e.getMessage());
        }
    }

    /**
     * Generate a unique chat ID based on the original filename and user ID
     */
    private String generateChatId(String originalFileName, Long userId) {
        String baseName = originalFileName;
        // Remove file extension if present
        if (originalFileName.contains(".")) {
            baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        }

        // Remove any non-alphanumeric characters and replace with underscores
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "_");

        // Add user ID to prevent conflicts between users
        return String.format("user%d_%s", userId, baseName);
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

    /**
     * DTO for zipper service job request
     */
    public static class ZipProcessingJobRequest {
        private String jobId;
        private Long userId;
        private String chatId;
        private String originalFilename;
        private String filePath;
        private Long fileSize;

        // Builder pattern
        public static ZipProcessingJobRequestBuilder builder() {
            return new ZipProcessingJobRequestBuilder();
        }

        public static class ZipProcessingJobRequestBuilder {
            private ZipProcessingJobRequest request = new ZipProcessingJobRequest();

            public ZipProcessingJobRequestBuilder jobId(String jobId) {
                request.jobId = jobId;
                return this;
            }

            public ZipProcessingJobRequestBuilder userId(Long userId) {
                request.userId = userId;
                return this;
            }

            public ZipProcessingJobRequestBuilder chatId(String chatId) {
                request.chatId = chatId;
                return this;
            }

            public ZipProcessingJobRequestBuilder originalFilename(String originalFilename) {
                request.originalFilename = originalFilename;
                return this;
            }

            public ZipProcessingJobRequestBuilder filePath(String filePath) {
                request.filePath = filePath;
                return this;
            }

            public ZipProcessingJobRequestBuilder fileSize(Long fileSize) {
                request.fileSize = fileSize;
                return this;
            }

            public ZipProcessingJobRequest build() {
                return request;
            }
        }

        // Getters and setters
        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getChatId() {
            return chatId;
        }

        public void setChatId(String chatId) {
            this.chatId = chatId;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public void setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        @Override
        public String toString() {
            return "ZipProcessingJobRequest{" + "jobId='" + jobId + '\'' + ", userId=" + userId
                    + ", chatId='" + chatId + '\'' + ", originalFilename='" + originalFilename
                    + '\'' + ", filePath='" + filePath + '\'' + ", fileSize=" + fileSize + '}';
        }
    }
}
