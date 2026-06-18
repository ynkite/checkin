package idusw.sbb.triplinker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class TripLinkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripLinkerApplication.class, args);
    }

}
