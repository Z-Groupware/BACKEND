package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.domain.model.Authority;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 구성원 관리 화면(§7)의 쓰기 창구 — {@link MemberDirectoryCommandPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional
public class MemberDirectoryCommandAdapter implements MemberDirectoryCommandPort {

    /** 역할 "없음". 전 회사 공용 시스템 행이다(V2.3.9) — {@link OwnerAccountAdapter} 와 같은 상수다. */
    private static final long ROLE_NONE_ID = 2L;

    /** 회사 안 이메일 유일성을 최종 차단하는 데이터베이스 제약 이름이다(V1). */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "UK_MEMBER_COMPANY_EMAIL";

    /** 부서당 활성 팀장 한 명을 최종 차단하는 데이터베이스 제약 이름이다(V2.3.19). */
    private static final String ACTIVE_TEAM_LEADER_UNIQUE_CONSTRAINT = "UK_MEMBER_ACTIVE_TEAM_LEADER";

    private final SpringDataMemberRepository memberRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * 팀장 승급이 여기로도 들어온다(§7-4). 호출자가 기존 팀장을 먼저 강등하지만 그 사이에 다른
     * 승급 요청이 끼어들 수 있어, {@code flush} 로 이 메서드 경계에서 제약 위반을 확인하고
     * 원시 SQL 예외 대신 {@code MEMBER_TEAM_LEADER_ALREADY_EXISTS} 로 변환한다.
     */
    @Override
    public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId, Long roleId) {
        MemberJpaEntity member = find(memberId);
        PositionRefEntity position = entityManager.getReference(PositionRefEntity.class, positionId);
        RoleRefEntity role = roleId == null ? null : entityManager.getReference(RoleRefEntity.class, roleId);
        member.changeRoleAndPosition(authority, position, role);
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
    }

    /**
     * 팀장 교체(§7-4)에서 기존 팀장 강등이 새 팀장 승급보다 먼저 이 메서드로 들어온다. 여기서
     * flush 하지 않으면 두 변경이 {@link #updateRoleAndPosition} 의 flush 하나로 함께 나가는데,
     * Hibernate 가 승급 쪽을 먼저 내보내면 그 순간 같은 부서에 활성 팀장이 둘이라
     * {@code UK_MEMBER_ACTIVE_TEAM_LEADER} 위반으로 정상 교체가 실패한다. 순서를 명시적으로
     * 고정한다.
     */
    @Override
    public void demoteToMember(Long memberId) {
        find(memberId).demoteToMember();
        memberRepository.flush();
    }

    @Override
    public void updateAdmin(Long memberId, boolean isAdmin) {
        find(memberId).changeAdmin(isAdmin);
    }

    /**
     * 삭제 시각은 {@link Clock} 에서 읽는다 — {@link MemberStatusAdapter#offboard} 와 같은 규칙이다.
     * {@code LocalDateTime.now()} 를 직접 부르면 테스트가 시간을 고정할 수 없다.
     */
    @Override
    public void softDelete(Long memberId) {
        find(memberId).softDelete(LocalDateTime.now(clock));
    }

    /**
     * 이메일 중복은 두 겹으로 막는다. 호출자(MemberDirectoryService)가 발급 전에 미리 조회해
     * 빠르게 거절하지만, 동시 요청은 그 확인과 이 INSERT 사이에 끼어들 수 있다 — 그래서
     * {@code saveAndFlush} 로 이 메서드 경계에서 제약 위반을 확인하고, 원시 SQL 예외 대신
     * {@code MEMBER_EMAIL_DUPLICATED} 로 변환한다(TeamPersistenceAdapter.create 와 같은 패턴).
     */
    @Override
    public Long issue(Long companyId, Long teamId, Long positionId, Long roleId,
                       String name, String email, String passwordHash, Authority authority) {
        return persist(companyId, teamId, positionId, roleId != null ? roleId : ROLE_NONE_ID,
                name, email, passwordHash, authority);
    }

    private Long persist(Long companyId, Long teamId, Long positionId, Long roleId,
                          String name, String email, String passwordHash, Authority authority) {
        CompanyJpaEntity company = entityManager.getReference(CompanyJpaEntity.class, companyId);
        TeamRefEntity team = entityManager.getReference(TeamRefEntity.class, teamId);
        PositionRefEntity position = entityManager.getReference(PositionRefEntity.class, positionId);
        RoleRefEntity role = entityManager.getReference(RoleRefEntity.class, roleId);

        MemberJpaEntity member = MemberJpaEntity.issue(company, team, role, position, name, email, passwordHash, authority);
        try {
            return memberRepository.saveAndFlush(member).getId();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
    }

    /* 아는 제약 위반만 공개 계약인 에러 코드로 바꾸고, 나머지 무결성 오류는 숨기지 않는다. */
    private RuntimeException translateConstraint(DataIntegrityViolationException exception) {
        if (containsConstraintName(exception, EMAIL_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.MEMBER_EMAIL_DUPLICATED, exception);
        }
        if (containsConstraintName(exception, ACTIVE_TEAM_LEADER_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS, exception);
        }
        return exception;
    }

    private boolean containsConstraintName(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains(constraintName.toUpperCase(Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private MemberJpaEntity find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
