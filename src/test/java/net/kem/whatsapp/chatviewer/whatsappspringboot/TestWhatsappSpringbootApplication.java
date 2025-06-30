package net.kem.whatsapp.chatviewer.whatsappspringboot;

import org.springframework.boot.SpringApplication;

public class TestWhatsappSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.from(WhatsappSpringbootApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
