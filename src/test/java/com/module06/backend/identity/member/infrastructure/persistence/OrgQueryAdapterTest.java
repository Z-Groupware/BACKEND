package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 인수인계가 이름·직급 스냅샷을 얻는 창구다. 스냅샷은 감사 기록으로 박히므로(writerNameSnap,
 * finalApproverNameSnap) 조회가 실패하면 승인 자체가 멈춘다.
 */
@DisplayName("OrgQueryAdapter")
@SpringBootTest
@Transactional
class OrgQueryAdapterTest {

    @Autowired
    private OrgQueryPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("이름과 직급을 스냅샷으로 준다")
    void findsMemberSnapshot() {
        insertCompany(301L);
        insertJobPosition(31L, 301L, "선임");
        insertMember(302L, 301L, null, 31L, "이하윤", "ACTIVE", null);

        OrgQueryPort.MemberSnapshot found = port.findMember(302L);

        assertThat(found.memberId()).isEqualTo(302L);
        assertThat(found.name()).isEqualTo("이하윤");
        assertThat(found.position()).isEqualTo("선임");
    }

    @Test
    @DisplayName("직급이 없는 오너도 조회된다 — position 은 null 이다")
    void findsOwnerWithoutPosition() {
        insertCompany(311L);
        insertMember(312L, 311L, null, null, "대표", "ACTIVE", null);

        OrgQueryPort.MemberSnapshot found = port.findMember(312L);

        assertThat(found.name()).isEqualTo("대표");
        assertThat(found.position()).isNull();
    }

    /*
     * 오프보딩 최종 승인은 계정을 soft delete 하면서 승인 기록을 남긴다. 여기서 퇴사자를 걸러내면
     * 그 순간 스냅샷 조회가 실패해 승인이 멈추고, 이미 끝난 인수인계의 이름도 못 읽는다.
     */
    @Test
    @DisplayName("퇴사자도 조회된다 — 감사 기록의 이름을 계속 읽어야 한다")
    void findsResignedMemberForAudit() {
        insertCompany(321L);
        insertMember(322L, 321L, null, null, "퇴사자", "RESIGNED", "2026-08-01 12:00:00");

        assertThat(port.findMember(322L).name()).isEqualTo("퇴사자");
    }

    @Test
    @DisplayName("없는 구성원은 MEMBER_NOT_FOUND — 조용히 null 을 주면 감사 기록에 빈 이름이 박힌다")
    void unknownMemberIsRejected() {
        assertThatThrownBy(() -> port.findMember(9999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("여러 명을 한 번에 조회한다 — 인사이트 조립용")
    void findsMembersInBatch() {
        insertCompany(331L);
        insertJobPosition(33L, 331L, "팀장");
        insertMember(332L, 331L, null, 33L, "김서준", "ACTIVE", null);
        insertMember(333L, 331L, null, null, "박하늘", "ACTIVE", null);

        List<OrgQueryPort.MemberSummary> found = port.findMembers(List.of(332L, 333L));

        assertThat(found).extracting(OrgQueryPort.MemberSummary::name)
                .containsExactlyInAnyOrder("김서준", "박하늘");
    }

    @Test
    @DisplayName("빈 목록을 주면 쿼리를 보내지 않고 빈 결과를 준다")
    void emptyBatchReturnsEmpty() {
        assertThat(port.findMembers(List.of())).isEmpty();
    }

    @Test
    @DisplayName("없는 id 가 섞여 있으면 그것만 빠진다 — 배치 조회는 전체를 실패시키지 않는다")
    void batchSkipsMissingIds() {
        insertCompany(341L);
        insertMember(342L, 341L, null, null, "있는사람", "ACTIVE", null);

        List<OrgQueryPort.MemberSummary> found = port.findMembers(List.of(342L, 9998L));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("있는사람");
    }

    @Test
    @DisplayName("팀의 리더를 찾는다")
    void findsTeamLeader() {
        insertCompany(351L);
        insertTeamWithLeader(35L, "개발팀", 352L);
        insertMember(352L, 351L, 35L, null, "팀장", "ACTIVE", null);

        assertThat(port.findTeamLeaderId(35L)).isEqualTo(352L);
    }

    @Test
    @DisplayName("리더가 지정되지 않은 팀은 null 이다 — 팀이 없는 것과 구분된다")
    void teamWithoutLeaderIsNull() {
        insertTeamWithLeader(36L, "무리더팀", null);

        assertThat(port.findTeamLeaderId(36L)).isNull();
    }

    // ── 픽스처 ──

    private void insertCompany(Long id) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, '(주)테스트')")
                .setParameter(1, id).setParameter(2, "C" + id).executeUpdate();
    }

    private void insertTeamWithLeader(Long id, String name, Long leaderMemberId) {
        em.createNativeQuery("INSERT INTO team (id, name, leader_member_id) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, name).setParameter(3, leaderMemberId)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    /*
     * company_id 를 넣지 않는다. 테스트 스키마는 ddl-auto: create-drop 으로 엔티티에서 생성되고,
     * JobPositionRefEntity 는 이 조회에 필요한 id·name 만 매핑한다 — 매핑하지 않은 컬럼은 H2 에 없다.
     */
    private void insertJobPosition(Long id, Long companyId, String name) {
        em.createNativeQuery("INSERT INTO job_position (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertMember(Long id, Long companyId, Long teamId, Long jobPositionId,
                             String name, String status, String deletedAt) {
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, job_position_id, email, password_hash,
                           name, role, is_admin, status, deleted_at)
                        VALUES (?, ?, ?, ?, ?, 'hash', ?, 'MEMBER', FALSE, ?, ?)
                        """)
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, teamId)
                .setParameter(4, jobPositionId).setParameter(5, "m" + id + "@x.co.kr")
                .setParameter(6, name).setParameter(7, status).setParameter(8, deletedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
