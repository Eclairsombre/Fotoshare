package local.epul4a.appreseaupartagephoto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "local.epul4a.appreseaupartagephoto",
        "local.epul4a.springbootdatajpa"
})
public class AppReseauPartagePhotoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppReseauPartagePhotoApplication.class, args);
    }
}
