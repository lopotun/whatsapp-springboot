package net.kem.whatsapp.chatviewer.whatsappspringboot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.CustomUserDetailsService;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.OAuth2AuthenticationFailureHandler;
import net.kem.whatsapp.chatviewer.whatsappspringboot.service.OAuth2AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2FailureHandler;

    @Autowired
    public SecurityConfig(CustomUserDetailsService userDetailsService,
            @Lazy OAuth2AuthenticationSuccessHandler oauth2SuccessHandler,
            @Lazy OAuth2AuthenticationFailureHandler oauth2FailureHandler) {
        this.userDetailsService = userDetailsService;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.oauth2FailureHandler = oauth2FailureHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/home", "/login", "/register", "/css/**", "/js/**",
                        "/images/**", "/h2-console/**")
                .permitAll().requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true").permitAll())
                .oauth2Login(
                        oauth2 -> oauth2.loginPage("/login").successHandler(oauth2SuccessHandler)
                                .failureHandler(oauth2FailureHandler).permitAll())
                .httpBasic(basic -> basic.realmName("WhatsApp Chat Viewer"))
                .logout(logout -> logout.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout=true").invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID").permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**").disable())
                .headers(headers -> headers.frameOptions(Customizer.withDefaults()).disable())
                .authenticationProvider(authenticationProvider());

        return http.build();
    }
}
