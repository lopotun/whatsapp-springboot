package net.kem.whatsapp.chatviewer.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"net.kem.whatsapp.chatviewer.frontend",
        "net.kem.whatsapp.chatviewer.shared"})
@EnableJpaRepositories(basePackages = {"net.kem.whatsapp.chatviewer.frontend.repository",
        "net.kem.whatsapp.chatviewer.shared.repository"})
@EntityScan(basePackages = {"net.kem.whatsapp.chatviewer.frontend.model",
        "net.kem.whatsapp.chatviewer.shared.model"})
public class WhatsappViewerFrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsappViewerFrontendApplication.class, args);
    }

}
