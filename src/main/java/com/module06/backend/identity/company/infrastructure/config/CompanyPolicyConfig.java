package com.module06.backend.identity.company.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.module06.backend.identity.company.domain.policy.CompanyCodeGenerator;
import com.module06.backend.identity.company.domain.policy.TemporaryPasswordGenerator;

/**
 * 무작위 생성기 두 개를 빈으로 올린다.
 *
 * <p>두 클래스에 {@code @Component} 를 붙이지 않은 이유 — 무작위 원천을 생성자로 받으므로 스프링이
 * 알아서 만들 수 없고, 만들 수 있게 기본 생성자를 열면 {@link java.util.Random} 이 섞여 들어올 길이
 * 생긴다. 팩토리를 여기서 한 번만 부르면 운영에 들어가는 원천이 {@link java.security.SecureRandom}
 * 하나로 고정된다.
 */
@Configuration
public class CompanyPolicyConfig {

    @Bean
    public CompanyCodeGenerator companyCodeGenerator() {
        return CompanyCodeGenerator.secure();
    }

    @Bean
    public TemporaryPasswordGenerator temporaryPasswordGenerator() {
        return TemporaryPasswordGenerator.secure();
    }
}
