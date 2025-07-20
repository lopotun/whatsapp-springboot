package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import lombok.RequiredArgsConstructor;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.ChatEntryEntity;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.ChatEntryService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebController {
    
    private final ChatEntryService chatEntryService;
    private final UserService userService;
    
    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }
    
    @GetMapping("/home")
    public String publicHome(Model model) {
        model.addAttribute("title", "Welcome to WhatsApp Chat Viewer");
        return "home";
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