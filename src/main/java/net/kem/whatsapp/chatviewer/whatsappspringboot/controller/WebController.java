package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class WebController {

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
