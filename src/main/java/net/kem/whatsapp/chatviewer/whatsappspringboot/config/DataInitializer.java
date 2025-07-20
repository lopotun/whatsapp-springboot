package net.kem.whatsapp.chatviewer.whatsappspringboot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.UserRepository;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(String... args) throws Exception {
        // Create default admin user if no users exist
        if (userRepository.count() == 0) {
            try {
                User adminUser = userService.registerUser(
                        "admin",
                        "password",
                        "admin@whatsappviewer.com",
                        "Admin",
                        "User"
                );
                
                // Set admin role
                userService.updateUserRole(adminUser.getId(), User.Role.ADMIN);
                
                log.info("Default admin user created: admin/password");
                log.info("Please change the default password after first login!");
            } catch (Exception e) {
                log.error("Failed to create default admin user: {}", e.getMessage());
            }
        }
    }
} 