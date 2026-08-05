package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이번 범위에 필요한 컬럼만 매핑한다 — 연차·테마는 마이페이지 API 에서 추가한다.
 *
 * <p>연관을 {@code LAZY} 로 두고 조회 쪽에서 {@code @EntityGraph} 로 함께 읽는다. {@code EAGER} 로
 * 두면 팀·직급이 필요 없는 조회에서도 매번 조인이 붙는다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberJpaEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private CompanyJpaEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamRefEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_team_id")
    private SubTeamRefEntity subTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id")
    private JobPositionRefEntity jobPosition;

    @Column(name = "email")
    private String email;

    /** 로그인 검증에만 쓴다. MyProfile 로는 절대 내보내지 않는다. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    /** 어드민 겸직. role 과 독립이다(V2.2.1). */
    @Column(name = "is_admin")
    private boolean isAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MemberStatus status;

    @Column(name = "joined_on")
    private LocalDate joinedOn;

    /** 퇴사 표시. 오프보딩 최종 승인 시 찍히고, 찍힌 회원은 조회되지 않는다. 본인 탈퇴 경로는 없다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
