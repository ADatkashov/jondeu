package kz.jondeu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class JondeuApplication {
    public static void main(String[] args) {
        SpringApplication.run(JondeuApplication.class, args);
    }
}
