package idusw.sbb.triplinker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /plan 이나 /plan/view 주소로 들어오면 무조건 index.html 화면을 띄워줌
        registry.addViewController("/plan").setViewName("index");
        registry.addViewController("/plan/view").setViewName("index");
    }
}