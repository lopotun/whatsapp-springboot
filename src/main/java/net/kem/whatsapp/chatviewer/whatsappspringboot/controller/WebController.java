package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    
    private final ChatEntryService chatEntryService;
    
    @Autowired
    public WebController(ChatEntryService chatEntryService) {
        this.chatEntryService = chatEntryService;
    }
    
    @GetMapping("/")
    public String home(Model model) {
        try {
            // Load recent entries for the home page
            Page<ChatEntryEntity> recentEntries = chatEntryService.findAll(0, 5);
            model.addAttribute("recentEntries", recentEntries.getContent());
            
            // Load basic statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEntries", recentEntries.getTotalElements());
            
            // Count unique authors from recent entries only to avoid performance issues
            long uniqueAuthors = recentEntries.getContent().stream()
                    .map(ChatEntryEntity::getAuthor)
                    .distinct()
                    .count();
            stats.put("uniqueAuthors", uniqueAuthors);
            
            // Calculate date range if there are entries
            if (!recentEntries.isEmpty()) {
                try {
                    List<ChatEntryEntity> content = recentEntries.getContent();
                    ChatEntryEntity first = content.get(0);
                    ChatEntryEntity last = content.get(content.size() - 1);
                    if (first.getLocalDateTime() != null && last.getLocalDateTime() != null) {
                        long daysDiff = java.time.Duration.between(first.getLocalDateTime(), last.getLocalDateTime()).toDays();
                        stats.put("dateRange", daysDiff + " days");
                    } else {
                        stats.put("dateRange", "N/A");
                    }
                } catch (Exception e) {
                    stats.put("dateRange", "N/A");
                }
            } else {
                stats.put("dateRange", "N/A");
            }
            
            // For now, set attachment count to 0 (can be enhanced later)
            stats.put("totalAttachments", 0);
            
            model.addAttribute("stats", stats);
            model.addAttribute("title", "Home");
            
            return "index";
        } catch (Exception e) {
            // If there's any error, return a simplified version
            model.addAttribute("recentEntries", new ArrayList<>());
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEntries", 0);
            stats.put("uniqueAuthors", 0);
            stats.put("dateRange", "N/A");
            stats.put("totalAttachments", 0);
            model.addAttribute("stats", stats);
            model.addAttribute("title", "Home");
            return "index";
        }
    }
    
    @GetMapping("/upload")
    public String upload(Model model) {
        model.addAttribute("title", "Upload");
        return "upload";
    }
    
    @GetMapping("/search")
    public String search(Model model) {
        model.addAttribute("title", "Search");
        return "search";
    }
    
    @GetMapping("/attachments")
    public String attachments(Model model) {
        model.addAttribute("title", "Attachments");
        return "attachments";
    }
    
    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("title", "Statistics");
        return "stats";
    }
} 