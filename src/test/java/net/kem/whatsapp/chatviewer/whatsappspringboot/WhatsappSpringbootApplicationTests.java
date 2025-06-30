package net.kem.whatsapp.chatviewer.whatsappspringboot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WhatsappSpringbootApplicationTests {

    @Test
    void contextLoads() {
    }

}
