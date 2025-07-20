package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.ChatEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {
    
    private final ChatEntryRepository chatEntryRepository;
    
    /**
     * Get all chat IDs for a user
     */
    public List<String> getUserChatIds(Long userId) {
        return chatEntryRepository.findDistinctChatIdsByUserId(userId);
    }
    
    /**
     * Get chat statistics for a user
     */
    public Map<String, Object> getChatStatistics(Long userId) {
        List<Object[]> stats = chatEntryRepository.getChatStatisticsByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        
        long totalChats = stats.size();
        long totalMessages = 0;
        long totalAttachments = 0;
        
        for (Object[] stat : stats) {
            String chatId = (String) stat[0];
            Long messageCount = (Long) stat[1];
            Long attachmentCount = (Long) stat[2];
            
            totalMessages += messageCount;
            totalAttachments += attachmentCount;
        }
        
        result.put("totalChats", totalChats);
        result.put("totalMessages", totalMessages);
        result.put("totalAttachments", totalAttachments);
        result.put("chatDetails", stats);
        
        return result;
    }
    
    /**
     * Get statistics for a specific chat
     */
    public Map<String, Object> getChatStatistics(Long userId, String chatId) {
        long messageCount = chatEntryRepository.countByUserIdAndChatId(userId, chatId);
        long attachmentCount = chatEntryRepository.findByUserIdAndChatId(userId, chatId)
                .stream()
                .filter(entry -> entry.getAttachmentHash() != null && !entry.getAttachmentHash().isEmpty())
                .count();
        
        Map<String, Object> result = new HashMap<>();
        result.put("chatId", chatId);
        result.put("messageCount", messageCount);
        result.put("attachmentCount", attachmentCount);
        
        return result;
    }
    
    /**
     * Delete a chat and all its entries
     */
    public void deleteChat(Long userId, String chatId) {
        chatEntryRepository.deleteByUserIdAndChatId(userId, chatId);
        log.info("Deleted chat: {} for user: {}", chatId, userId);
    }
    
    /**
     * Get chat summary for a user
     */
    public Map<String, Object> getChatSummary(Long userId) {
        List<String> chatIds = getUserChatIds(userId);
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("totalChats", chatIds.size());
        summary.put("chatIds", chatIds);
        
        // Get basic stats for each chat
        Map<String, Object> chatStats = new HashMap<>();
        for (String chatId : chatIds) {
            Map<String, Object> stats = getChatStatistics(userId, chatId);
            chatStats.put(chatId, stats);
        }
        summary.put("chatStats", chatStats);
        
        return summary;
    }
    
    /**
     * Check if a chat exists for a user
     */
    public boolean chatExists(Long userId, String chatId) {
        return chatEntryRepository.countByUserIdAndChatId(userId, chatId) > 0;
    }
    
    /**
     * Get total storage used by a user (approximate)
     */
    public long getTotalStorageUsed(Long userId) {
        // This is a rough estimate - in a real implementation you might want to track actual file sizes
        long totalEntries = chatEntryRepository.countByUserId(userId);
        // Assume average entry size of 500 bytes
        return totalEntries * 500;
    }
}