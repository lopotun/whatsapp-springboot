package net.kem.whatsapp.chatviewer.whatsappspringboot.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout, Model model) {
        if (error != null) {
            if ("oauth".equals(error)) {
                model.addAttribute("error",
                        "Google authentication failed. Please try again or use your username and password.");
            } else {
                model.addAttribute("error", "Invalid username or password.");
            }
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new RegistrationRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(
            @Valid @ModelAttribute("user") RegistrationRequest registrationRequest,
            BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "auth/register";
        }

        try {
            User user = userService.registerUser(registrationRequest.getUsername(),
                    registrationRequest.getPassword(), registrationRequest.getEmail(),
                    registrationRequest.getFirstName(), registrationRequest.getLastName());

            // Auto-login after registration
            Authentication authentication =
                    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                            registrationRequest.getUsername(), registrationRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            redirectAttributes.addFlashAttribute("success",
                    "Registration successful! Welcome, " + user.getFirstName() + "!");
            return "redirect:/dashboard";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();

        // Try to find user by username first (traditional auth)
        Optional<User> userOpt = userService.findByUsername(principalName);

        // If not found, try to find by OAuth2 ID (OAuth2 auth)
        if (userOpt.isEmpty() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthToken.getName();
            userOpt = userService.findByOauthProviderAndOauthId(provider, oauthId);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            model.addAttribute("user", user);
            userService.updateLastLogin(user.getUsername());
        } else {
            // Create a default user object to prevent template errors
            User defaultUser = User.builder().firstName("User").lastName("").email(principalName)
                    .username(principalName).createdAt(LocalDateTime.now()).build();
            model.addAttribute("user", defaultUser);
        }

        return "dashboard";
    }

    @GetMapping("/profile")
    public String showProfile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();

        // Try to find user by username first (traditional auth)
        Optional<User> userOpt = userService.findByUsername(principalName);

        // If not found, try to find by OAuth2 ID (OAuth2 auth)
        if (userOpt.isEmpty() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthToken.getName();
            userOpt = userService.findByOauthProviderAndOauthId(provider, oauthId);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            model.addAttribute("user", user);
        } else {
            // Create a default user object to prevent template errors
            User defaultUser = User.builder().firstName("User").lastName("").email(principalName)
                    .username(principalName).createdAt(LocalDateTime.now()).build();
            model.addAttribute("user", defaultUser);
        }

        return "auth/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/profile";
        }

        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error",
                    "Password must be at least 6 characters long.");
            return "redirect:/profile";
        }

        boolean success = userService.changePassword(username, oldPassword, newPassword);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect.");
        }

        return "redirect:/profile";
    }

    // REST API endpoints for future OAuth integration
    @PostMapping("/api/auth/register")
    @ResponseBody
    public Map<String, Object> registerUserApi(@RequestBody RegistrationRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.registerUser(request.getUsername(), request.getPassword(),
                    request.getEmail(), request.getFirstName(), request.getLastName());

            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("userId", user.getId());
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    // Static inner class for registration request
    public static class RegistrationRequest {
        private String username;
        private String password;
        private String email;
        private String firstName;
        private String lastName;

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}
