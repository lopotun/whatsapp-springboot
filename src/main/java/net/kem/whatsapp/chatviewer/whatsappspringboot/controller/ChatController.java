package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    private final UserService userService;
    
    /**
     * Get all chat IDs for the authenticated user
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getUserChats() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        return userService.findByUsername(username)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("userId", user.getId());
                    response.put("username", username);
                    response.put("chatIds", chatService.getUserChatIds(user.getId()));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get chat statistics for the authenticated user
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getChatStatistics() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        return userService.findByUsername(username)
                .map(user -> {
                    Map<String, Object> stats = chatService.getChatStatistics(user.getId());
                    stats.put("username", username);
                    return ResponseEntity.ok(stats);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get statistics for a specific chat
     */
    @GetMapping("/{chatId}/stats")
    public ResponseEntity<Map<String, Object>> getChatStatistics(@PathVariable String chatId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        var user = userOpt.get();
        if (!chatService.chatExists(user.getId(), chatId)) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> stats = chatService.getChatStatistics(user.getId(), chatId);
        stats.put("username", username);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Delete a specific chat
     */
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Map<String, Object>> deleteChat(@PathVariable String chatId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        var user = userOpt.get();
        if (!chatService.chatExists(user.getId(), chatId)) {
            return ResponseEntity.notFound().build();
        }
        
        chatService.deleteChat(user.getId(), chatId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Chat deleted successfully");
        response.put("chatId", chatId);
        response.put("username", username);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get chat summary for the authenticated user
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getChatSummary() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        var user = userOpt.get();
        Map<String, Object> summary = chatService.getChatSummary(user.getId());
        summary.put("username", username);
        summary.put("storageUsed", chatService.getTotalStorageUsed(user.getId()));
        
        return ResponseEntity.ok(summary);
    }
}