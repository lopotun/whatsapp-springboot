package net.kem.whatsapp.chatviewer.zipper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"net.kem.whatsapp.chatviewer.zipper",
                "net.kem.whatsapp.chatviewer.shared"})
@EnableJpaRepositories(basePackages = {"net.kem.whatsapp.chatviewer.zipper.repository",
                "net.kem.whatsapp.chatviewer.shared.repository"})
@EntityScan(basePackages = {"net.kem.whatsapp.chatviewer.zipper.model",
                "net.kem.whatsapp.chatviewer.shared.model"})
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class ZipperServiceApplication {

        public static void main(String[] args) {
                SpringApplication.run(ZipperServiceApplication.class, args);
        }
}
