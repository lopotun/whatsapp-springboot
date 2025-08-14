package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class WebController {

    @Autowired
    private UserService userService;

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
        addUserToModel(model);
        return "upload";
    }

    @GetMapping("/search")
    public String search(Model model) {
        model.addAttribute("title", "Search");
        addUserToModel(model);
        return "search";
    }

    @GetMapping("/attachments")
    public String attachments(Model model) {
        model.addAttribute("title", "Attachments");
        addUserToModel(model);
        return "attachments";
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("title", "Statistics");
        addUserToModel(model);
        return "stats";
    }

    /**
     * Add user information to the model for OAuth2 and traditional users
     */
    private void addUserToModel(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();

        System.out.println("DEBUG: Principal name: " + principalName);
        System.out.println(
                "DEBUG: Authentication type: " + authentication.getClass().getSimpleName());

        // Try to find user by username first (traditional auth)
        Optional<User> userOpt = userService.findByUsername(principalName);
        System.out.println("DEBUG: User found by username: " + userOpt.isPresent());

        // If not found, try to find by OAuth2 ID (OAuth2 auth)
        if (userOpt.isEmpty() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthToken.getName();
            System.out.println("DEBUG: OAuth2 provider: " + provider + ", ID: " + oauthId);
            userOpt = userService.findByOauthProviderAndOauthId(provider, oauthId);
            System.out.println("DEBUG: User found by OAuth2: " + userOpt.isPresent());
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println(
                    "DEBUG: User found: " + user.getFirstName() + " " + user.getLastName());
            model.addAttribute("user", user);
        } else {
            // Create a default user object to prevent template errors
            User defaultUser = User.builder().firstName("User").lastName("").email(principalName)
                    .username(principalName).createdAt(LocalDateTime.now()).build();
            System.out.println("DEBUG: Using default user: " + defaultUser.getFirstName());
            model.addAttribute("user", defaultUser);
        }
    }
}
