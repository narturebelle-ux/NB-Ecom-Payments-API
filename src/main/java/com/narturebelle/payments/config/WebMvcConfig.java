package com.narturebelle.payments.config;

import com.narturebelle.payments.security.ConsentCheckInterceptor;
import com.narturebelle.payments.security.VerifiedCheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final VerifiedCheckInterceptor verifiedCheckInterceptor;
    private final ConsentCheckInterceptor consentCheckInterceptor;

    public WebMvcConfig(VerifiedCheckInterceptor verifiedCheckInterceptor, ConsentCheckInterceptor consentCheckInterceptor) {
        this.verifiedCheckInterceptor = verifiedCheckInterceptor;
        this.consentCheckInterceptor = consentCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(verifiedCheckInterceptor);
        registry.addInterceptor(consentCheckInterceptor);
    }
}
