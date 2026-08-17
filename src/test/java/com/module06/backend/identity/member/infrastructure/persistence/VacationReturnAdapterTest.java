package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.VacationReturnPort;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 휴직 자동 복귀의 경계를 못박는다.
 *
 * 이 테스트의 핵심은 하루 차이다 — "종료일이 지나면"의 해석이 갈리면 8/20 종료자가 8/20 에
 * 복직할지 8/21 에 복직할지가 달라지고, 그 차이는 배포 뒤 데이터로 굳어 되돌리기 어렵다.
 * 팀 확정은 "8/20 까지 휴직, 8/21 부터 재직"이다(2026-08-16).
 *
 * MemberStatusAdapterTest 와 같은 슬라이스(@SpringBootTest + @Transactional + 네이티브 INSERT)를
 * 쓴다. 이 어댑터는 handover 테이블까지 조인해서 판정하므로 목으로는 규칙을 확인할 수 없다.
 */
@DisplayName("VacationReturnAdapter — 휴직 자동 복귀")
@SpringBootTest
@Transactional
class VacationReturnAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Autowired
    private VacationReturnPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("경계 3점 — 종료일이 지난 사람만 복직한다(당일·미래는 휴직 유지)")
    void 경계_3점() {
        insertMember(301L, "VACATION");
        insertFinalizedVacation(1301L, 301L, TODAY.minusDays(1));   // 어제 끝났다
        insertMember(302L, "VACATION");
        insertFinalizedVacation(1302L, 302L, TODAY);                // 오늘이 종료일
        insertMember(303L, "VACATION");
        insertFinalizedVacation(1303L, 303L, TODAY.plusDays(1));    // 내일 끝난다

        List<Long> returned = port.returnExpiredVacations(TODAY);

        assertThat(returned).containsExactly(301L);
        assertThat(statusOf(301L)).isEqualTo("ACTIVE");
        assertThat(statusOf(302L)).as("종료일 당일까지는 휴직이다").isEqualTo("VACATION");
        assertThat(statusOf(303L)).isEqualTo("VACATION");
    }

    /*
     * 데이터가 어긋난 경우다. 강제로 복직시키지 않는 이유는 종료일을 모르기 때문이다 —
     * 임의로 오늘 복직시키면 실제 휴직 기간이 남아 있는 사람을 근무 중으로 돌려놓게 된다.
     */
    @Test
    @DisplayName("승인된 휴직 기록이 없으면 건드리지 않는다 — 복직 시점을 알 수 없다")
    void 휴직기록이_없으면_건너뛴다() {
        insertMember(304L, "VACATION");                              // handover 행 자체가 없다
        insertMember(305L, "VACATION");
        insertVacation(1305L, 305L, "SUBMITTED", TODAY.minusDays(5)); // 아직 승인 전이다

        List<Long> returned = port.returnExpiredVacations(TODAY);

        assertThat(returned).isEmpty();
        assertThat(statusOf(304L)).isEqualTo("VACATION");
        assertThat(statusOf(305L)).isEqualTo("VACATION");
    }

    /*
     * 휴직을 다시 신청해 승인받으면 FINALIZED VACATION 행이 두 개가 된다. 오래된 행을 보면
     * 이미 연장된 휴직을 끝난 것으로 판정한다 — 반대로 최신 행이 지난 날짜면 복직해야 한다.
     */
    @Test
    @DisplayName("휴직 기록이 여러 개면 가장 최근 것(id 최대)으로 판정한다")
    void 최신_휴직기록을_본다() {
        insertMember(306L, "VACATION");
        insertFinalizedVacation(1306L, 306L, TODAY.plusDays(30));    // 오래된 행 — 아직 안 끝났다
        insertFinalizedVacation(1307L, 306L, TODAY.minusDays(1));    // 최신 행 — 어제 끝났다

        assertThat(port.returnExpiredVacations(TODAY)).containsExactly(306L);
        assertThat(statusOf(306L)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("종료일이 비어 있는 승인 기록은 대상이 아니다")
    void 종료일이_없으면_건너뛴다() {
        insertMember(307L, "VACATION");
        insertFinalizedVacation(1308L, 307L, null);

        assertThat(port.returnExpiredVacations(TODAY)).isEmpty();
        assertThat(statusOf(307L)).isEqualTo("VACATION");
    }

    /*
     * 휴직이 아닌 사람은 애초에 조회 대상이 아니다. 특히 WAITING(승인 대기)이 휩쓸리면 아직
     * 결재가 끝나지 않은 신청이 조용히 없던 일이 된다.
     */
    @Test
    @DisplayName("휴직 상태가 아니면 종료일이 지났어도 건드리지 않는다")
    void 휴직자만_대상이다() {
        insertMember(308L, "WAITING");
        insertFinalizedVacation(1309L, 308L, TODAY.minusDays(10));
        insertMember(309L, "ACTIVE");
        insertFinalizedVacation(1310L, 309L, TODAY.minusDays(10));

        assertThat(port.returnExpiredVacations(TODAY)).isEmpty();
        assertThat(statusOf(308L)).isEqualTo("WAITING");
        assertThat(statusOf(309L)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("휴직자가 없으면 빈 리스트다 — handover 를 읽지 않는다")
    void 휴직자가_없으면_빈리스트() {
        assertThat(port.returnExpiredVacations(TODAY)).isEmpty();
    }

    private String statusOf(Long memberId) {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT status FROM member WHERE id = ?")
                .setParameter(1, memberId).getSingleResult();
    }

    private void insertFinalizedVacation(Long handoverId, Long writerMemberId, LocalDate leaveEndDate) {
        insertVacation(handoverId, writerMemberId, "FINALIZED", leaveEndDate);
    }

    private void insertVacation(Long handoverId, Long writerMemberId, String status, LocalDate leaveEndDate) {
        /* created_at·updated_at·version 은 운영 DDL 에 DEFAULT 가 있지만 테스트 H2 스키마에는
         * 반영되지 않아 NOT NULL 위반이 난다 — 값을 명시적으로 넣는다. */
        em.createNativeQuery("""
                        INSERT INTO handover
                          (id, writer_member_id, team_id, handover_type, status,
                           leave_start_at, leave_end_at, writer_name_snap, writer_position_snap,
                           version, created_at, updated_at)
                        VALUES (?, ?, 1, 'VACATION', ?, NULL, ?, '테스트', '사원',
                                0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, handoverId)
                .setParameter(2, writerMemberId)
                .setParameter(3, status)
                /* 시각 부분은 판정에서 버려진다 — 날짜만 맞으면 된다. */
                .setParameter(4, leaveEndDate == null ? null : leaveEndDate.atTime(18, 0))
                .executeUpdate();
        em.flush();
        em.clear();
    }

    /* MemberStatusAdapterTest 의 삽입 규약을 그대로 따른다 — 회사 id 는 구성원 id 와 같게 잡는다. */
    private void insertMember(Long id, String status) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, '(주)테스트')")
                .setParameter(1, id).setParameter(2, "C" + id).executeUpdate();
        /* role_id 는 NOT NULL 이다(V2.3.10) — 시드 행 "없음"(id 2)을 그대로 흉내 낸다. */
        em.createNativeQuery("MERGE INTO role (id, name) KEY(id) VALUES (2, '없음')").executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, role_id, email, password_hash, name,
                           authority, is_admin, status, deleted_at)
                        VALUES (?, ?, NULL, 2, ?, 'hash', '테스트', 'MEMBER', FALSE, ?, NULL)
                        """)
                .setParameter(1, id).setParameter(2, id)
                .setParameter(3, "m" + id + "@x.co.kr").setParameter(4, status)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
