package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
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

    /*
     * ── 생애주기 전이 ────────────────────────────────────────────────────────
     *
     * 상태 enum 과 전이 규칙은 이 도메인이 소유한다(MemberStatusPort javadoc). 그래서 setter 를
     * 열지 않고 의도 이름의 메서드만 노출하고, 각 메서드가 출발 상태를 직접 검사한다.
     *
     *   ACTIVE ─[requestLeave]→ WAITING ─[approveVacation]→ VACATION
     *                             │       └[offboard]→ deleted_at + RESIGNED
     *                             └──────[restoreActive]→ ACTIVE
     *
     * 검사를 서비스가 아니라 여기서 하는 이유: 호출 경로가 늘어나도 규칙이 한 곳에만 있게 된다.
     * setter 를 열면 다음 사람이 상태를 임의로 바꿔 이 그림이 무의미해진다.
     */

    /** 인수인계 상신 즉시. (ACTIVE → WAITING) */
    public void requestLeave() {
        requireStatus(MemberStatus.ACTIVE);
        this.status = MemberStatus.WAITING;
    }

    /** 휴직 최종 승인. (WAITING → VACATION) */
    public void approveVacation() {
        requireStatus(MemberStatus.WAITING);
        this.status = MemberStatus.VACATION;
    }

    /** 반려 — 재직으로 원복. (WAITING → ACTIVE) */
    public void restoreActive() {
        requireStatus(MemberStatus.WAITING);
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * 오프보딩 최종 승인. 물리 삭제하지 않는다 — 감사 흔적을 남기려면 행이 있어야 한다.
     * {@code deleted_at} 이 찍히면 조회에서 걸러지고, 로그인은 403 이 된다.
     */
    public void offboard(LocalDateTime at) {
        requireStatus(MemberStatus.WAITING);
        this.status = MemberStatus.RESIGNED;
        this.deletedAt = at;
    }

    /**
     * 출발 상태가 아니면 거절한다. RESIGNED 는 어떤 전이도 통과하지 못한다 — 되살리는 경로가
     * 없기 때문에 여기서 따로 분기하지 않아도 자연히 막힌다.
     */
    private void requireStatus(MemberStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(AuthErrorCode.MEMBER_STATUS_TRANSITION_INVALID);
        }
    }
}
