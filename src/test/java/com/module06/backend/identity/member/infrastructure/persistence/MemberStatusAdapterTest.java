package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.MemberStatusPort;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.auth.infrastructure.persistence.InMemoryRefreshTokenStore;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 인수인계·휴직이 요청하는 멤버 생애주기다. 상태 enum 과 전이 규칙은 이 도메인이 소유하므로
 * (MemberStatusPort javadoc), 잘못된 순서로 부르면 거절하는 것까지 여기서 고정한다.
 *
 *   ACTIVE ─[toWaiting]→ WAITING ─[toVacation]→ VACATION
 *                          │      └[offboard]→ deleted_at + RESIGNED
 *                          └──[restoreActive]→ ACTIVE
 */
@DisplayName("MemberStatusAdapter")
@SpringBootTest
@Transactional
class MemberStatusAdapterTest {

    /*
     * 운영 구현은 Redis 를 쓴다. 테스트는 Redis 없이 돌아야 하므로(계획 Task 3) 같은 계약의
     * 인메모리 구현을 끼운다 — 모의 객체가 아니라 실제 구현이라 "정말 폐기됐는지"를 상태로 확인한다.
     */
    @TestConfiguration
    static class InMemoryStoreConfig {
        @Bean
        @Primary
        RefreshTokenStore inMemoryRefreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }
    }

    @Autowired
    private MemberStatusPort port;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("신청 즉시 재직에서 대기로 바꾼다")
    void activeToWaiting() {
        insertMember(201L, "ACTIVE", null);

        port.toWaiting(201L);

        assertThat(statusOf(201L)).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("휴직 최종승인은 대기에서 휴직으로 바꾼다")
    void waitingToVacation() {
        insertMember(202L, "WAITING", null);

        port.toVacation(202L);

        assertThat(statusOf(202L)).isEqualTo("VACATION");
    }

    @Test
    @DisplayName("반려는 대기에서 재직으로 되돌린다")
    void waitingToActive() {
        insertMember(203L, "WAITING", null);

        port.restoreActive(203L);

        assertThat(statusOf(203L)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("오프보딩은 deleted_at 을 찍고 상태를 RESIGNED 로 바꾼다 — 물리 삭제는 하지 않는다")
    void offboardSoftDeletes() {
        insertMember(204L, "WAITING", null);

        port.offboard(204L);

        assertThat(statusOf(204L)).isEqualTo("RESIGNED");
        assertThat(deletedAtOf(204L)).isNotNull();
        assertThat(existsRow(204L)).as("행이 남아 있어야 한다").isTrue();
    }

    @Test
    @DisplayName("오프보딩은 갱신표까지 폐기한다 — 안 지우면 퇴사자가 14일간 스스로 재발급한다")
    void offboardRevokesRefreshTokens() {
        insertMember(209L, "WAITING", null);
        refreshTokenStore.save(209L, "jti-desktop", Duration.ofDays(14));
        refreshTokenStore.save(209L, "jti-mobile", Duration.ofDays(14));

        port.offboard(209L);

        assertThat(refreshTokenStore.exists(209L, "jti-desktop")).isFalse();
        assertThat(refreshTokenStore.exists(209L, "jti-mobile")).isFalse();
    }

    @Test
    @DisplayName("전이가 거절되면 갱신표를 건드리지 않는다 — 잘못된 요청으로 남의 세션을 끊으면 안 된다")
    void rejectedOffboardKeepsTokens() {
        insertMember(210L, "ACTIVE", null);
        refreshTokenStore.save(210L, "jti-alive", Duration.ofDays(1));

        assertTransitionRejected(() -> port.offboard(210L));

        assertThat(refreshTokenStore.exists(210L, "jti-alive")).isTrue();
    }

    @Test
    @DisplayName("재직자를 바로 휴직으로 보낼 수 없다 — 신청·승인 절차를 건너뛴 것이다")
    void cannotSkipWaitingOnVacation() {
        insertMember(205L, "ACTIVE", null);

        assertTransitionRejected(() -> port.toVacation(205L));
        assertThat(statusOf(205L)).as("거절되면 상태가 그대로여야 한다").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("재직자를 바로 오프보딩할 수 없다")
    void cannotSkipWaitingOnOffboard() {
        insertMember(206L, "ACTIVE", null);

        assertTransitionRejected(() -> port.offboard(206L));
        assertThat(deletedAtOf(206L)).isNull();
    }

    @Test
    @DisplayName("이미 대기 중인 사람을 다시 대기로 보낼 수 없다 — 인수인계가 두 건 열린 상태다")
    void cannotRequestTwice() {
        insertMember(207L, "WAITING", null);

        assertTransitionRejected(() -> port.toWaiting(207L));
    }

    @Test
    @DisplayName("퇴사자는 어떤 전이도 받지 않는다 — 되살리는 경로가 없다")
    void resignedIsTerminal() {
        insertMember(208L, "RESIGNED", "2026-08-01 12:00:00");

        assertTransitionRejected(() -> port.toWaiting(208L));
        assertTransitionRejected(() -> port.restoreActive(208L));
        assertTransitionRejected(() -> port.toVacation(208L));
    }

    @Test
    @DisplayName("없는 구성원은 MEMBER_NOT_FOUND")
    void unknownMemberIsRejected() {
        assertThatThrownBy(() -> port.toWaiting(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
    }

    private void assertTransitionRejected(Runnable transition) {
        assertThatThrownBy(transition::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_STATUS_TRANSITION_INVALID);
    }

    private String statusOf(Long memberId) {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT status FROM member WHERE id = ?")
                .setParameter(1, memberId).getSingleResult();
    }

    private Object deletedAtOf(Long memberId) {
        em.flush();
        em.clear();
        return em.createNativeQuery("SELECT deleted_at FROM member WHERE id = ?")
                .setParameter(1, memberId).getSingleResult();
    }

    private boolean existsRow(Long memberId) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM member WHERE id = ?")
                .setParameter(1, memberId).getSingleResult();
        return count.intValue() == 1;
    }

    private void insertMember(Long id, String status, String deletedAt) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, '(주)테스트')")
                .setParameter(1, id).setParameter(2, "C" + id).executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, email, password_hash, name, role, is_admin, status, deleted_at)
                        VALUES (?, ?, ?, 'hash', '테스트', 'MEMBER', FALSE, ?, ?)
                        """)
                .setParameter(1, id).setParameter(2, id).setParameter(3, "m" + id + "@x.co.kr")
                .setParameter(4, status).setParameter(5, deletedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
