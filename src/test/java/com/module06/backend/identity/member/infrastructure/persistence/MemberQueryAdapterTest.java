package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * MEET-01 참석자 roster 조립용 배치 조회다. 회사 경계·삭제 여부를 여기서 걸러낸다.
 */
@DisplayName("MemberQueryAdapter")
@SpringBootTest
@Transactional
class MemberQueryAdapterTest {

    @Autowired
    private MemberQueryPort port;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("회사 안의 활성 구성원을 배치로 찾고, 팀 정보를 함께 채운다")
    void findsActiveMembersWithTeam() {
        insertCompany(501L);
        insertTeam(51L, "개발팀");
        insertMember(502L, 501L, 51L, "이하윤", "ACTIVE", null);
        insertMember(503L, 501L, null, "김서준", "ACTIVE", null);

        List<MemberSnapshot> found = port.findActiveMembers(501L, List.of(502L, 503L));

        assertThat(found).hasSize(2);
        MemberSnapshot withTeam = found.stream().filter(m -> m.memberId().equals(502L)).findFirst().orElseThrow();
        assertThat(withTeam.name()).isEqualTo("이하윤");
        assertThat(withTeam.teamId()).isEqualTo(51L);
        assertThat(withTeam.teamName()).isEqualTo("개발팀");
        MemberSnapshot withoutTeam = found.stream().filter(m -> m.memberId().equals(503L)).findFirst().orElseThrow();
        assertThat(withoutTeam.teamId()).isNull();
        assertThat(withoutTeam.teamName()).isNull();
    }

    @Test
    @DisplayName("직급 정보를 함께 채운다 — 없으면 null이다(MEET-04)")
    void findsActiveMembersWithPosition() {
        insertCompany(541L);
        insertPosition(54L, 541L, "선임");
        insertMemberWithPosition(542L, 541L, null, "이하윤", "ACTIVE", null, 54L);
        insertMember(543L, 541L, null, "직급없음", "ACTIVE", null);

        List<MemberSnapshot> found = port.findActiveMembers(541L, List.of(542L, 543L));

        MemberSnapshot withPosition = found.stream().filter(m -> m.memberId().equals(542L)).findFirst().orElseThrow();
        assertThat(withPosition.positionName()).isEqualTo("선임");
        MemberSnapshot withoutPosition = found.stream().filter(m -> m.memberId().equals(543L)).findFirst().orElseThrow();
        assertThat(withoutPosition.positionName()).isNull();
    }

    @Test
    @DisplayName("다른 회사 구성원은 결과에서 빠진다")
    void excludesMembersFromOtherCompanies() {
        insertCompany(511L);
        insertCompany(512L);
        insertMember(513L, 511L, null, "우리회사", "ACTIVE", null);
        insertMember(514L, 512L, null, "남의회사", "ACTIVE", null);

        List<MemberSnapshot> found = port.findActiveMembers(511L, List.of(513L, 514L));

        assertThat(found).extracting(MemberSnapshot::memberId).containsExactly(513L);
    }

    @Test
    @DisplayName("삭제된(퇴사) 구성원은 결과에서 빠진다")
    void excludesDeletedMembers() {
        insertCompany(521L);
        insertMember(522L, 521L, null, "재직자", "ACTIVE", null);
        insertMember(523L, 521L, null, "퇴사자", "RESIGNED", "2026-08-01 12:00:00");

        List<MemberSnapshot> found = port.findActiveMembers(521L, List.of(522L, 523L));

        assertThat(found).extracting(MemberSnapshot::memberId).containsExactly(522L);
    }

    @Test
    @DisplayName("존재하지 않는 id 는 조용히 빠진다 — 전체를 실패시키지 않는다")
    void skipsUnknownIds() {
        insertCompany(531L);
        insertMember(532L, 531L, null, "있는사람", "ACTIVE", null);

        List<MemberSnapshot> found = port.findActiveMembers(531L, List.of(532L, 999_999L));

        assertThat(found).extracting(MemberSnapshot::memberId).containsExactly(532L);
    }

    @Test
    @DisplayName("id 목록이 비어 있으면 조회 없이 빈 목록을 준다")
    void emptyIdsReturnsEmpty() {
        assertThat(port.findActiveMembers(1L, List.of())).isEmpty();
    }

    @Test
    @DisplayName("findMembersIncludingDeleted 는 퇴사자도 포함한다 — 과거 회의 명단 보존용")
    void findMembersIncludingDeletedIncludesResignedMembers() {
        insertCompany(551L);
        insertMember(552L, 551L, null, "재직자", "ACTIVE", null);
        insertMember(553L, 551L, null, "퇴사자", "RESIGNED", "2026-08-01 12:00:00");

        List<MemberSnapshot> found = port.findMembersIncludingDeleted(551L, List.of(552L, 553L));

        assertThat(found).extracting(MemberSnapshot::memberId).containsExactlyInAnyOrder(552L, 553L);
    }

    @Test
    @DisplayName("findMembersIncludingDeleted 도 다른 회사·존재하지 않는 id 는 걸러낸다")
    void findMembersIncludingDeletedExcludesOtherCompaniesAndUnknownIds() {
        insertCompany(561L);
        insertCompany(562L);
        insertMember(563L, 561L, null, "우리회사", "ACTIVE", null);
        insertMember(564L, 562L, null, "남의회사", "ACTIVE", null);

        List<MemberSnapshot> found = port.findMembersIncludingDeleted(561L, List.of(563L, 564L, 999_999L));

        assertThat(found).extracting(MemberSnapshot::memberId).containsExactly(563L);
    }

    @Test
    @DisplayName("findMembersIncludingDeleted 도 id 목록이 비어 있으면 조회 없이 빈 목록을 준다")
    void findMembersIncludingDeletedEmptyIdsReturnsEmpty() {
        assertThat(port.findMembersIncludingDeleted(1L, List.of())).isEmpty();
    }

    private void insertCompany(Long id) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, '(주)테스트')")
                .setParameter(1, id).setParameter(2, "C" + id).executeUpdate();
    }

    private void insertTeam(Long id, String name) {
        em.createNativeQuery("INSERT INTO team (id, name) VALUES (?, ?)")
                .setParameter(1, id).setParameter(2, name).executeUpdate();
    }

    private void insertMember(Long id, Long companyId, Long teamId, String name, String status, String deletedAt) {
        insertMemberWithPosition(id, companyId, teamId, name, status, deletedAt, null);
    }

    private void insertMemberWithPosition(Long id, Long companyId, Long teamId, String name, String status,
                                           String deletedAt, Long positionId) {
        /* role_id 는 NOT NULL 이다(V2.3.10) — 시드 행 "없음"(id 2)을 그대로 흉내 낸다. */
        em.createNativeQuery("MERGE INTO role (id, name) KEY(id) VALUES (2, '없음')").executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, role_id, position_id, email, password_hash, name, authority,
                           is_admin, status, deleted_at)
                        VALUES (?, ?, ?, 2, ?, ?, 'hash', ?, 'MEMBER', FALSE, ?, ?)
                        """)
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, teamId)
                .setParameter(4, positionId)
                .setParameter(5, "m" + id + "@x.co.kr").setParameter(6, name)
                .setParameter(7, status).setParameter(8, deletedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private void insertPosition(Long id, Long companyId, String name) {
        em.createNativeQuery("INSERT INTO position (id, company_id, name) VALUES (?, ?, ?)")
                .setParameter(1, id).setParameter(2, companyId).setParameter(3, name)
                .executeUpdate();
    }
}
