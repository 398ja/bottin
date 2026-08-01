package xyz.tcheeric.bottin.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "xyz.tcheeric.bottin.core",
        "xyz.tcheeric.bottin.client"
})
public class BottinClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(BottinClientApplication.class, args);
    }
}
