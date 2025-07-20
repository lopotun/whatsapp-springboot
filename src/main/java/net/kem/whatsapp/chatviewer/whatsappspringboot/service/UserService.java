package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import net.kem.whatsapp.chatviewer.whatsappspringboot.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public User registerUser(String username, String password, String email, String firstName, String lastName) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(User.Role.USER)
                .enabled(true)
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("Registered new user: {}", username);
        return savedUser;
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId) {
        return userRepository.findByOauthProviderAndOauthId(oauthProvider, oauthId);
    }
    
    public User createOrUpdateOAuthUser(String oauthProvider, String oauthId, String email, 
                                       String firstName, String lastName, String profilePictureUrl) {
        return userRepository.findByOauthProviderAndOauthId(oauthProvider, oauthId)
                .map(existingUser -> {
                    // Update existing OAuth user
                    existingUser.setEmail(email);
                    existingUser.setFirstName(firstName);
                    existingUser.setLastName(lastName);
                    existingUser.setProfilePictureUrl(profilePictureUrl);
                    existingUser.setLastLogin(LocalDateTime.now());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // Create new OAuth user
                    String username = oauthProvider.toLowerCase() + "_" + oauthId;
                    User newUser = User.builder()
                            .username(username)
                            .password("") // OAuth users don't need password
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .role(User.Role.USER)
                            .enabled(true)
                            .oauthProvider(oauthProvider)
                            .oauthId(oauthId)
                            .profilePictureUrl(profilePictureUrl)
                            .build();
                    
                    User savedUser = userRepository.save(newUser);
                    log.info("Created new OAuth user: {} ({})", username, oauthProvider);
                    return savedUser;
                });
    }
    
    public void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }
    
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
        log.info("Deleted user with ID: {}", userId);
    }
    
    public User updateUserRole(Long userId, User.Role role) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setRole(role);
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
    }
    
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (passwordEncoder.matches(oldPassword, user.getPassword())) {
                        user.setPassword(passwordEncoder.encode(newPassword));
                        userRepository.save(user);
                        log.info("Password changed for user: {}", username);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }
} 