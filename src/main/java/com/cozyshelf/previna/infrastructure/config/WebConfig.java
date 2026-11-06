package com.cozyshelf.previna.infrastructure.config;

import com.cozyshelf.previna.infrastructure.security.AutorizacaoAntesDaValidacao;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new AutorizacaoAntesDaValidacao());
    }
}
