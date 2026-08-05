package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, Long> {

    /**
     * 회사·팀·하위팀·직급을 한 쿼리로 함께 읽는다.
     *
     * <p>{@code @EntityGraph} 를 쓰는 이유는 두 가지다. {@code open-in-view: false} 라서 트랜잭션을
     * 벗어난 뒤 연관에 손대면 지연 로딩 예외가 나고, 하나씩 읽으면 회원 한 명 조회에 쿼리가 다섯 번
     * 나간다. /me 는 모든 화면 진입에서 호출되므로 그 차이가 그대로 드러난다.
     */
    @EntityGraph(attributePaths = {"company", "team", "subTeam", "jobPosition"})
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
}
