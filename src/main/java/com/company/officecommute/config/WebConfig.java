package com.company.officecommute.config;

import com.company.officecommute.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/commute/**", "/annual-leave/**", "/overtime/**")
                // TODO: /employee/** 추후 세밀 조정
                .excludePathPatterns("/login", "/logout", "/team/**", "/h2-console/**", "/employee");
    }
}
