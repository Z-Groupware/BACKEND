package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

    /*
     * IDENTITY 다. 지금까지 이 엔티티로 INSERT 한 적이 없어(읽기 전용 어댑터뿐) 전략이 비어 있었고,
     * 그 상태로 save 하면 id 가 null 인 채 나간다. member.id 는 AUTO_INCREMENT 다(V1).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private CompanyJpaEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamRefEntity team;

    /** 화면의 "역할". 구 sub_team 이다(V2.3.2·V2.3.4). 인가에 쓰지 않는 라벨이다. role_id 는 NOT NULL 이다(V2.3.10). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleRefEntity role;

    /** 화면의 "직급". 구 job_position 이다(V2.3.3·V2.3.5). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private PositionRefEntity position;

    @Column(name = "email")
    private String email;

    /** 로그인 검증에만 쓴다. MyProfile 로는 절대 내보내지 않는다. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    /** 화면의 "권한". 구 role 컬럼이다(V2.3.1) — 이름을 role 테이블에 내줬다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Authority authority;

    /** 어드민 겸직. authority 와 독립이다(V2.2.1). */
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

    /**
     * 기업 등록(API 27)이 만드는 오너. 이 경로 말고는 OWNER 가 생기지 않는다.
     *
     * <p>{@code isAdmin} 을 인자로 받지 않고 {@code true} 로 고정한다 — 오너가 어드민 기능을 못 쓰는
     * 상태가 한 순간도 있으면 안 된다(AUTH_AUTHZ_SPEC §2-1-1). 등록 직후에는 회사에 오너뿐이라,
     * 여기서 켜지 않으면 계정을 만들 사람이 아무도 없어 회사가 잠긴다.
     *
     * <p>팀·직급을 받지 않는다. 온보딩 전이라 정해진 값이 없고, 화면에도 오너를 넣는 칸이 없다.
     * 역할만 "없음"(id 2)으로 채운다 — {@code role_id} 는 NOT NULL 이다(V2.3.10).
     */
    static MemberJpaEntity owner(CompanyJpaEntity company, RoleRefEntity noRole,
                                 String name, String email, String passwordHash) {
        MemberJpaEntity member = new MemberJpaEntity();
        member.company = company;
        member.role = noRole;
        member.name = name;
        member.email = email;
        member.passwordHash = passwordHash;
        member.authority = Authority.OWNER;
        member.isAdmin = true;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    /**
     * 계정 발급(API 12·§5-1). 발급 즉시 재직이다 — 계정 승인 단계가 없다.
     *
     * <p>{@code isAdmin} 을 인자로 받지 않고 항상 {@code false} 로 고정한다 — 발급 시점에는 관리 권한을
     * 줄 수 없고, §7-7 로 오너가 발급 후 따로 부여한다(어드민의 자기 복제 차단이 이 모델의 요점).
     */
    static MemberJpaEntity issue(CompanyJpaEntity company, TeamRefEntity team, RoleRefEntity role,
                                 PositionRefEntity position, String name, String email, String passwordHash,
                                 Authority authority) {
        MemberJpaEntity member = new MemberJpaEntity();
        member.company = company;
        member.team = team;
        member.role = role;
        member.position = position;
        member.name = name;
        member.email = email;
        member.passwordHash = passwordHash;
        member.authority = authority;
        member.isAdmin = false;
        member.status = MemberStatus.ACTIVE;
        member.joinedOn = LocalDate.now();
        return member;
    }

    /**
     * 권한·직급·역할 라벨 동시 변경(§7-4). 하나만 열면 "직급은 팀장인데 권한은 멤버" 같은 중간
     * 상태가 저장될 수 있어 같이 받는다. isAdmin 은 여기서 건드리지 않는다 — §7-7 전용이다.
     *
     * <p>{@code role} 만 null 을 "안 바꾼다"로 읽는다. role_id 는 NOT NULL 이라(V2.3.10) null 을
     * 그대로 대입하면 플러시에서 제약 위반으로 터진다 — 역할을 비우는 것은 "없음" 행을 넣는 것이다.
     */
    public void changeRoleAndPosition(Authority authority, PositionRefEntity position, RoleRefEntity role) {
        this.authority = authority;
        this.position = position;
        if (role != null) {
            this.role = role;
        }
    }

    /** 관리 권한(겸직) 부여·회수(§7-7). role 은 건드리지 않는다 — 그게 이 모델의 요점이다. */
    public void changeAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    /** 팀장 교체 부수효과(§7-4) — 기존 리더를 멤버로 내린다. 직급은 건드리지 않는다. */
    public void demoteToMember() {
        this.authority = Authority.MEMBER;
    }

    /**
     * 마이페이지 셀프 프로필 수정. null 인 인자는 건드리지 않는다 — 부분 수정. authority·isAdmin은
     * 이 메서드로 절대 바뀌지 않는다({@link #changeRoleAndPosition}·{@link #changeAdmin} 전용).
     */
    void updateProfile(TeamRefEntity team, PositionRefEntity position, String phone) {
        if (team != null) {
            this.team = team;
        }
        if (position != null) {
            this.position = position;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

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
     *   (어느 상태에서든) ─[softDelete]→ deleted_at + DELETED
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
     *
     * <p>권한도 함께 회수한다({@code MemberStatusPort#offboard} 계약의 "권한 회수"). 팀장으로
     * 남겨두면 조직 화면이 퇴사자를 팀장으로 계속 그리고, 후임 승급 경로가 팀장 자리를 이미
     * 찬 것으로 본다. OWNER 는 건드리지 않는다 — 소유자 이관은 별도 절차다.
     */
    public void offboard(LocalDateTime at) {
        requireStatus(MemberStatus.WAITING);
        this.status = MemberStatus.RESIGNED;
        this.deletedAt = at;
        if (this.authority == Authority.LEADER) {
            this.authority = Authority.MEMBER;
        }
    }

    /**
     * 관리자의 사원 삭제(§7). 오프보딩(=인수인계 절차를 밟은 퇴사)과 달리 승인 흐름 없이 계정을
     * 닫는다. 물리 삭제하지 않는 이유는 {@link #offboard} 와 같다 — 회의·인수인계가 이 행을
     * 참조하므로 지우면 그 이력이 끊긴다.
     *
     * <p>RESIGNED 가 아니라 DELETED 를 찍는다(V2.3.21). 두 값이 같아지면 "인계를 하고 나갔는지"를
     * 데이터로 답할 수 없다.
     *
     * <p>출발 상태를 제한하지 않는다 — 재직·휴직·대기 어느 쪽이든 관리자는 계정을 닫을 수 있어야
     * 한다. 이미 닫힌 계정만 막는다. {@link #offboard} 와 마찬가지로 팀장 권한은 회수한다.
     */
    public void softDelete(LocalDateTime at) {
        if (this.deletedAt != null) {
            throw new BusinessException(AuthErrorCode.MEMBER_STATUS_TRANSITION_INVALID);
        }
        this.status = MemberStatus.DELETED;
        this.deletedAt = at;
        if (this.authority == Authority.LEADER) {
            this.authority = Authority.MEMBER;
        }
    }

    /**
     * 출발 상태가 아니면 거절한다. RESIGNED·DELETED 는 어떤 전이도 통과하지 못한다 — 되살리는
     * 경로가 없기 때문에 여기서 따로 분기하지 않아도 자연히 막힌다.
     */
    private void requireStatus(MemberStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(AuthErrorCode.MEMBER_STATUS_TRANSITION_INVALID);
        }
    }
}
