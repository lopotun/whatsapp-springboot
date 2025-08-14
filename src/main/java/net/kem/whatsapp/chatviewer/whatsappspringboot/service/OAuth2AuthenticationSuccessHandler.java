package net.kem.whatsapp.chatviewer.whatsappspringboot.service;

import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import net.kem.whatsapp.chatviewer.whatsappspringboot.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    @Autowired
    public OAuth2AuthenticationSuccessHandler(@Lazy UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String oauthId = oauthUser.getName();

            // Extract user information from OAuth2 user
            Map<String, Object> attributes = oauthUser.getAttributes();
            String email = (String) attributes.get("email");
            String firstName = (String) attributes.get("given_name");
            String lastName = (String) attributes.get("family_name");
            String profilePictureUrl = (String) attributes.get("picture");

            log.info("OAuth2 authentication successful for provider: {}, oauthId: {}, email: {}",
                    provider, oauthId, email);

            try {
                // Create or update user in database
                User user = userService.createOrUpdateOAuthUser(provider, oauthId, email, firstName,
                        lastName, profilePictureUrl);
                log.info("User processed successfully: {}", user.getUsername());
            } catch (Exception e) {
                log.error("Error processing OAuth2 user: {}", e.getMessage(), e);
                // Continue with authentication even if user processing fails
            }
        }

        // Redirect to dashboard on successful authentication
        setDefaultTargetUrl("/dashboard");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
