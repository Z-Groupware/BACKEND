package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.identity.member.domain.model.MemberStatus;

interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, Long> {

    /**
     * 회사·팀·하위팀·직급을 한 쿼리로 함께 읽는다.
     *
     * <p>{@code @EntityGraph} 를 쓰는 이유는 두 가지다. {@code open-in-view: false} 라서 트랜잭션을
     * 벗어난 뒤 연관에 손대면 지연 로딩 예외가 나고, 하나씩 읽으면 회원 한 명 조회에 쿼리가 다섯 번
     * 나간다. /me 는 모든 화면 진입에서 호출되므로 그 차이가 그대로 드러난다.
     */
    @EntityGraph(attributePaths = {"company", "team", "role", "position"})
    Optional<MemberJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 로그인용. 퇴사자도 걸러내지 않는다 — 호출자가 "비번 틀림"과 "퇴사"를 구분해 답해야 한다.
     *
     * <p>team 을 함께 읽는 이유: teamId 가 토큰 클레임에 들어가는데, 지연 프록시에서 식별자만 꺼내는
     * 동작에 기대면 매핑이 조금 바뀔 때 조용히 추가 쿼리가 붙는다.
     */
    @EntityGraph(attributePaths = {"team"})
    Optional<MemberJpaEntity> findByCompanyIdAndEmail(Long companyId, String email);

    /**
     * 재발급용. 상속받은 {@code findById} 를 덮어 team 을 함께 읽게 만든다 —
     * {@link #findByCompanyIdAndEmail} 과 같은 이유다(teamId 가 토큰 클레임에 들어간다).
     *
     * <p>{@link #findByIdAndDeletedAtIsNull} 을 쓰지 않는 이유: 그건 퇴사자를 걸러내므로
     * "퇴사했다"와 "그런 구성원이 없다"가 같은 결과가 된다. 재발급은 그 둘을 다르게 답해야 한다.
     */
    @Override
    @EntityGraph(attributePaths = {"team"})
    Optional<MemberJpaEntity> findById(Long id);

    /**
     * 회사 소속 구성원의 id 만. 오너·어드민의 "회사 전체" 인수인계 조회 범위로 쓴다.
     *
     * <p>엔티티가 아니라 프로젝션으로 받는 이유: id 만 필요한데 엔티티를 읽으면 비밀번호 해시까지
     * 메모리에 올라온다. 신규 {@code @Query} 는 Gate 1(QUERY_002)이 막으므로 파생 메서드로 푼다 —
     * {@code CompanyId} 는 {@code company.id} 로 해석된다({@link #findByCompanyIdAndEmail} 과 같다).
     *
     * <p>퇴사자를 걸러내지 않는다. 오프보딩이 끝난 작성자를 빼면 방금 승인한 인수인계가 목록에서
     * 사라져 감사 흔적을 볼 수 없다 — 진행 중 여부는 호출자가 status 로 가른다.
     */
    List<MemberIdProjection> findByCompanyId(Long companyId);

    /** id 한 컬럼만 읽는 닫힌 프로젝션. */
    interface MemberIdProjection {
        Long getId();
    }

    /**
     * 회의 참석자 roster 배치 조회용(MEET-01, {@code MemberQueryPort}). 다른 회사·삭제된
     * 구성원은 조건에서 자연히 빠진다 — 호출자가 따로 걸러낼 필요가 없다.
     *
     * <p>team 을 함께 읽는 이유: roster 응답에 teamName 이 들어가는데, 지연 프록시에 기대면
     * N+1 이 붙는다({@link #findByCompanyIdAndEmail} 과 같은 이유).
     */
    @EntityGraph(attributePaths = {"team", "position"})
    List<MemberJpaEntity> findByCompanyIdAndIdInAndDeletedAtIsNull(Long companyId, List<Long> ids);

    /**
     * 과거(종료된) 회의 참석자 명단 보존용({@code MemberQueryPort#findMembersIncludingDeleted}).
     * {@link #findByCompanyIdAndIdInAndDeletedAtIsNull} 과 달리 퇴사자도 포함한다 — 다른 회사·
     * 존재하지 않는 id 는 {@code companyId} 조건으로 자연히 빠진다.
     */
    @EntityGraph(attributePaths = {"team", "position"})
    List<MemberJpaEntity> findByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    /**
     * 구성원 관리 화면(§7) 전용 — 목록·조직도·좌석 수 세기가 전부 이 스냅샷 하나에서 갈린다.
     * 필터·검색·페이징은 서비스가 Java 단에서 한다(TeamService.buildContext 와 같은 이유) —
     * Gate 1(QUERY_002)이 신규 {@code @Query} 를 막아 조건절을 늘려가는 파생 메서드를 새로 파지 않는다.
     */
    @EntityGraph(attributePaths = {"team", "role", "position"})
    List<MemberJpaEntity> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    /** 계정 발급 이메일 중복 확인(§5-1). 퇴사자의 이메일은 다시 쓸 수 있다. */
    boolean existsByCompanyIdAndEmailAndDeletedAtIsNull(Long companyId, String email);

    /**
     * 휴직 자동 복귀 배치 전용({@code VacationReturnAdapter}). 상태로 찾는 유일한 메서드다.
     *
     * <p>신규 {@code @Query} 는 Gate 1(QUERY_002)이 막으므로 파생 메서드로 푼다
     * ({@link #findByCompanyId} 와 같은 이유).
     */
    /*
     * TENANT_001 예외: 전사 배치라 요청자가 없어 회사로 좁힐 수 없다. 다른 조회는 호출자의
     * companyId 로 좁히지만 여기는 부르는 사람이 사람이 아니라 스케줄러다 — 회사를 하나 골라
     * 넣을 근거가 없고, 회사별로 나눠 돌면 회사 수만큼 쿼리가 늘 뿐 결과가 같다.
     * 읽는 값도 상태 전이에만 쓰이고 응답으로 나가지 않는다.
     */
    // nosemgrep: tenant-derived-query-without-company-scope
    List<MemberJpaEntity> findByStatusAndDeletedAtIsNull(MemberStatus status);
}
