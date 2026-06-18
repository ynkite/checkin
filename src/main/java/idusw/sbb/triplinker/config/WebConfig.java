package idusw.sbb.triplinker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url}")
    private String uploadUrl;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/plan").setViewName("index");
        registry.addViewController("/plan/view").setViewName("index");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/posts/** 요청을 로컬 파일 경로로 매핑
        registry.addResourceHandler(uploadUrl + "**")
                .addResourceLocations("file:///" + uploadPath);
    }
}