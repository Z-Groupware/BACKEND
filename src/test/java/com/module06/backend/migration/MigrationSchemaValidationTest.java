package com.module06.backend.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway 마이그레이션을 실제 MySQL에 적용하고 ddl-auto=validate로 엔티티-스키마 정합성을 검증한다.
 * (docs/DB_MIGRATION_RULES.md §11 "운영 반영 전 validate + migrate 확인"의 실제 구현.)
 *
 * <p>다른 테스트는 H2(빠름, Flyway 비활성)로 돈다 — 여기서만 실제 MySQL을 쓴다.
 * 컨텍스트 로딩 자체가 검증이다: Flyway가 SQL 문법/순서에서 실패하거나, Hibernate validate가
 * 엔티티-스키마 불일치를 잡으면 컨텍스트 로딩이 예외를 던지고 이 테스트가 실패한다.
 *
 * <p>{@code @Tag("migration")}로 기본 {@code test} task에서 제외되고, 별도 {@code migrationCheck}
 * task(Docker 필요)로만 실행된다 — build.gradle 참조.
 */
@Tag("migration")
@Testcontainers
@Import(MigrationSchemaValidationTest.ExcludeArchitectureFixtureEntities.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MigrationSchemaValidationTest {

    // 태그 고정 — latest는 재현성이 깨진다. 운영 실제 patch 버전은 인프라팀 확인 필요.
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.40").withDatabaseName("module06");

    @DynamicPropertySource
    static void overrideTestDefaults(DynamicPropertyRegistry registry) {
        // src/test/resources/application.yaml의 H2 기본값(flyway 비활성·create-drop)을 뒤집는다.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.out-of-order", () -> "false"); // 운영 재현
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void migratesAndMatchesEntities() {
        // 의도적으로 비어 있다 — 실패는 컨텍스트 로딩 단계(BeanCreationException)에서 난다.
    }

    /**
     * {@code architecture.fixture}는 ArchitectureRulesTest의 ARCH_002 자체검증용 {@code @Entity}
     * 픽스처(FrameworkBoundModel)를 담고 있다 — 실제 테이블이 없는데 {@code @SpringBootTest}의
     * 기본 엔티티 스캔(앱 base package 하위 전체)에 걸려 관리 대상이 되면서 validate가 깨졌다.
     * ArchUnit은 바이트코드를 직접 읽어 이 컨텍스트와 무관하므로, 여기서만 안전하게 제외한다.
     */
    @TestConfiguration
    static class ExcludeArchitectureFixtureEntities {
        @Bean
        ManagedClassNameFilter managedClassNameFilter() {
            return className -> !className.startsWith("com.module06.backend.architecture.fixture");
        }
    }
}
