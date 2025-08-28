package net.kem.whatsapp.chatviewer.frontend.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.kem.whatsapp.chatviewer.frontend.service.ZipperNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/zipper/notifications")
@RequiredArgsConstructor
@Slf4j
public class ZipperNotificationController {

    private final ZipperNotificationService zipperNotificationService;

    /**
     * Receive processing completion notification from Zipper service
     */
    @PostMapping
    public ResponseEntity<String> receiveNotification(@RequestBody Map<String, Object> notification,
            @RequestHeader("X-API-Key") String apiKey) {

        try {
            log.info("Received notification from Zipper service with API key: {}",
                    apiKey.substring(0, Math.min(8, apiKey.length())) + "...");

            zipperNotificationService.handleProcessingResult(notification);

            return ResponseEntity.ok("Notification received successfully");

        } catch (Exception e) {
            log.error("Error processing Zipper notification: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error processing notification");
        }
    }

    /**
     * Health check endpoint for Zipper service
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Zipper notification endpoint is healthy");
    }
}
