package com.company.officecommute.config;

import com.company.officecommute.auth.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // TODO: .addPathPatterns("/**") 을 하고 exclude를 제대로 설정하는걸로 고치기 !!!
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/commute/**", "/annual-leave/**", "/overtime/**", "/team/**", "/employee/**")
                // .addPathPatterns("/**")
                // TODO: /employee/** 추후 세밀 조정
                .excludePathPatterns("/login", "/logout", "/h2-console/**", "/");
    }
}
