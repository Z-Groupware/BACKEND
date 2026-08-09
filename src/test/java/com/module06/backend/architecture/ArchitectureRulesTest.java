package com.module06.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gate 1 · 결정론 하드 게이트 — {@code review-loop/rules.yaml}의 ARCH_001~003을 집행한다.
 *
 * <p><b>이 클래스의 존재 자체가 Gate 1의 스위치다.</b> {@code .githooks/pre-push}가 파일 존재를 검사해
 * 자동으로 Gate 1을 켠다(없으면 스킵). 메서드 이름은 rules.yaml의 {@code archunit_ref}와 <b>정확히</b>
 * 일치해야 한다 — 규칙과 집행을 잇는 유일한 끈이다.
 *
 * <p><b>왜 레이어가 없는데 지금 쓰는가</b>: rules.yaml ARCH_003 주석이 "신규 레포 — 부채(baseline) 없이
 * 위반 0에서 시작"이라고 못박았다. 코드가 생긴 <b>뒤에</b> 규칙을 넣으면 이미 쌓인 위반을 예외 처리하며
 * 시작해야 한다(그게 baseline 부채다). 그래서 레이어가 비어 있는 지금 넣는다:
 * 대상 클래스가 0개인 동안은 통과하고, 누가 해당 패키지를 만드는 순간부터 강제된다.
 *
 * <p><b>빈 상태로 통과하는 규칙은 그 자체로 위험하다</b> — Gate 2가 "차단 게이트"라고 문서화된 채
 * 실제로는 아무것도 차단하지 못했던 것과 같은 함정이다(UNIFIED_DESIGN §8). 그래서 아래
 * {@link RuleActuallyFires}가 <b>의도적 위반 픽스처로 규칙이 실제로 실패하는지</b>를 함께 검증한다.
 * 규칙이 벙어리가 되면 그 중첩 테스트가 깨진다.
 *
 * <p><b>패키지 이름은 이 테스트가 정하지 않는다</b> — rules.yaml의 규칙 문구가 정한다
 * (presentation · domain.model · domain.repository · application · infrastructure).
 * 팀이 다른 이름을 쓰기로 하면 rules.yaml과 아래 상수를 함께 고쳐야 한다.
 */
@DisplayName("Gate 1 · 아키텍처 규칙 (ARCH_001~003)")
class ArchitectureRulesTest {

    private static final String ROOT = "com.module06.backend";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        // 테스트 클래스는 제외 — 규칙은 프로덕션 코드에만 적용한다(테스트는 어느 레이어든 참조 가능).
        // 덕분에 아래 fixture 패키지의 의도적 위반이 실제 게이트를 깨뜨리지 않는다.
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    // ── 규칙 정의 — 루트 접두사를 받는다. 실제 게이트는 ROOT로, 자기검증은 fixture 루트로 같은 규칙을 쓴다. ──

    /** ARCH_001 — 컨트롤러가 리포지토리를 직접 부르면 트랜잭션 경계·유스케이스가 컨트롤러로 새어 나온다. */
    static ArchRule arch001(String root) {
        return noClasses().that().resideInAPackage(root + ".presentation..")
                .should().dependOnClassesThat().resideInAPackage(root + ".domain.repository..")
                .because("컨트롤러는 Service를 경유한다 (rules.yaml ARCH_001)")
                .allowEmptyShould(true);   // 레이어 생성 전에는 대상 0개 → 통과. 생기면 강제.
    }

    /** ARCH_002 — 도메인 모델이 프레임워크에 묶이면 단위 테스트가 컨테이너를 요구하고 이식성이 사라진다. */
    static ArchRule arch002(String root) {
        return noClasses().that().resideInAPackage(root + ".domain.model..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "javax.persistence..")
                .because("도메인 모델은 순수 POJO다 (rules.yaml ARCH_002)")
                .allowEmptyShould(true);
    }

    /** ARCH_003 — 유스케이스가 어댑터를 직접 알면 의존 방향이 뒤집혀 구현 교체가 불가능해진다. */
    static ArchRule arch003(String root) {
        return noClasses().that().resideInAPackage(root + ".application..")
                .should().dependOnClassesThat().resideInAPackage(root + ".infrastructure..")
                .because("포트-어댑터: 의존은 안쪽으로만 (rules.yaml ARCH_003)")
                .allowEmptyShould(true);
    }

    /** rules.yaml에는 없는 최소 가드 — 레이어를 나눠도 도메인이 서로 물려 있으면 결국 한 덩어리다. */
    static ArchRule domainCycles(String root) {
        return slices().matching(root + ".domain.(*)..").should().beFreeOfCycles()
                .allowEmptyShould(true);
    }

    // ── 실제 게이트 (프로덕션 코드) ─────────────────────────────────────────

    @Test
    @DisplayName("ARCH_001 · presentation은 domain.repository를 직접 참조하지 않는다")
    void presentationMustNotAccessRepositoryDirectly() {
        arch001(ROOT).check(productionClasses);
    }

    @Test
    @DisplayName("ARCH_002 · domain.model은 Spring·JPA에 의존하지 않는다")
    void domainModelMustNotDependOnFramework() {
        arch002(ROOT).check(productionClasses);
    }

    @Test
    @DisplayName("ARCH_003 · application은 infrastructure를 직접 참조하지 않는다")
    void applicationMustNotDependOnInfrastructure() {
        arch003(ROOT).check(productionClasses);
    }

    @Test
    @DisplayName("도메인 패키지 간 순환 참조가 없다")
    void domainsMustBeFreeOfCycles() {
        domainCycles(ROOT).check(productionClasses);
    }

    /**
     * 규칙이 벙어리가 아님을 증명한다 — 의도적 위반 픽스처를 넣고 <b>실패해야</b> 통과.
     *
     * <p>이게 없으면 "레이어가 없어서 통과"와 "규칙이 잘못 짜여서 아무것도 안 잡음"을 구분할 수 없다.
     * 픽스처는 {@code architecture/fixture/} 아래 테스트 소스이므로 실제 게이트(프로덕션 임포터)에는
     * 보이지 않는다. 픽스처에서 위반을 없애면 이 테스트가 깨진다 — 일부러 그렇게 뒀다.
     */
    @Nested
    @DisplayName("규칙이 실제로 위반을 잡는다 (빈 통과와 벙어리 규칙 구분)")
    class RuleActuallyFires {

        private static final String FIXTURE = ROOT + ".architecture.fixture";

        private JavaClasses fixtureClasses() {
            // 픽스처는 테스트 소스라 DO_NOT_INCLUDE_TESTS를 걸지 않는다.
            return new ClassFileImporter().importPackages(FIXTURE);
        }

        @Test
        @DisplayName("ARCH_001 — presentation→repository 위반을 잡는다")
        void detectsPresentationToRepository() {
            assertThatThrownBy(() -> arch001(FIXTURE).check(fixtureClasses()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("ViolatingController");
        }

        @Test
        @DisplayName("ARCH_002 — domain.model의 JPA 의존을 잡는다")
        void detectsFrameworkBoundDomainModel() {
            assertThatThrownBy(() -> arch002(FIXTURE).check(fixtureClasses()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("FrameworkBoundModel");
        }

        @Test
        @DisplayName("ARCH_003 — application→infrastructure 위반을 잡는다")
        void detectsApplicationToInfrastructure() {
            assertThatThrownBy(() -> arch003(FIXTURE).check(fixtureClasses()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("ViolatingService");
        }

        @Test
        @DisplayName("도메인 순환(cart↔order)을 잡는다")
        void detectsDomainCycle() {
            assertThatThrownBy(() -> domainCycles(FIXTURE).check(fixtureClasses()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Cycle");
        }

        @Test
        @DisplayName("대상 패키지가 아예 없으면 통과한다 (레이어 생성 전 상태)")
        void passesWhenNoClassesMatch() {
            JavaClasses empty = new ClassFileImporter().importPackages(FIXTURE + ".nonexistent");

            assertThatCode(() -> arch001(FIXTURE + ".nonexistent").check(empty))
                    .doesNotThrowAnyException();
        }
    }
}
