package com.cozyshelf.previna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.TimeZone;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PrevinaApplication {
    public static void main(String[] args) {
        // O mesmo fuso de hibernate.jdbc.time_zone. Os contêineres rodam em UTC, e sem isto LocalDate.now()
        // e @FutureOrPresent viram o dia seguinte às 21h de Brasília: marcar ou confirmar um exame para
        // hoje seria recusado. Definido aqui, e não no compose, para valer também no mvn spring-boot:run.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(PrevinaApplication.class, args);
    }
}
