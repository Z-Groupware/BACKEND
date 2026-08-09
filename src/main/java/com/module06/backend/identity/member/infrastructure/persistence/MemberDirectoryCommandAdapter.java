package com.module06.backend.identity.member.infrastructure.persistence;

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

    /**
     * 팀장 승급이 여기로도 들어온다(§7-4). 호출자가 기존 팀장을 먼저 강등하지만 그 사이에 다른
     * 승급 요청이 끼어들 수 있어, {@code flush} 로 이 메서드 경계에서 제약 위반을 확인하고
     * 원시 SQL 예외 대신 {@code MEMBER_TEAM_LEADER_ALREADY_EXISTS} 로 변환한다.
     */
    @Override
    public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId) {
        MemberJpaEntity member = find(memberId);
        PositionRefEntity position = entityManager.getReference(PositionRefEntity.class, positionId);
        member.changeRoleAndPosition(authority, position);
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
    }

    @Override
    public void demoteToMember(Long memberId) {
        find(memberId).demoteToMember();
    }

    @Override
    public void updateAdmin(Long memberId, boolean isAdmin) {
        find(memberId).changeAdmin(isAdmin);
    }

    /**
     * 이메일 중복은 두 겹으로 막는다. 호출자(MemberDirectoryService)가 발급 전에 미리 조회해
     * 빠르게 거절하지만, 동시 요청은 그 확인과 이 INSERT 사이에 끼어들 수 있다 — 그래서
     * {@code saveAndFlush} 로 이 메서드 경계에서 제약 위반을 확인하고, 원시 SQL 예외 대신
     * {@code MEMBER_EMAIL_DUPLICATED} 로 변환한다(TeamPersistenceAdapter.create 와 같은 패턴).
     */
    @Override
    public Long issue(Long companyId, Long teamId, Long positionId, String roleLabel,
                       String name, String email, String passwordHash, Authority authority) {
        if (roleLabel != null) {
            /*
             * 화면 폼에 없는 값이라(§5-1) roleLabel 로 role 을 찾는 조회 창구가 아직 없다.
             * 조용히 "없음"으로 발급하면 호출자가 지정한 역할이 사라진 채 성공 응답이 나가므로,
             * 여기서 명시적으로 막는다 — 다만 외부 요청으로 도달 가능한 값이라 500 이 아니라
             * BusinessException(400)으로 응답해야 한다(UnsupportedOperationException 은
             * GlobalExceptionHandler 의 catch-all 로 떨어져 500 이 나간다).
             */
            throw new BusinessException(AuthErrorCode.MEMBER_ROLE_LABEL_NOT_SUPPORTED);
        }
        return issue(companyId, teamId, positionId, ROLE_NONE_ID, name, email, passwordHash, authority);
    }

    @Override
    public Long issueWithRole(Long companyId, Long teamId, Long positionId, Long roleId,
                               String name, String email, String passwordHash, Authority authority) {
        return issue(companyId, teamId, positionId, roleId != null ? roleId : ROLE_NONE_ID,
                name, email, passwordHash, authority);
    }

    private Long issue(Long companyId, Long teamId, Long positionId, Long roleId,
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
