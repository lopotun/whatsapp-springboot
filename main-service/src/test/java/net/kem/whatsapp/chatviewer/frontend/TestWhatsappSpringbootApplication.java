package net.kem.whatsapp.chatviewer.frontend;

import org.springframework.boot.SpringApplication;

public class TestWhatsappSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.from(WhatsappViewerFrontendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
