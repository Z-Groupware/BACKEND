package com.module06.backend.identity.member.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;
import com.module06.backend.identity.member.domain.model.Plan;

/**
 * 구성원 관리 화면(§7)이 읽는 창구. 목록·조직도·상세가 전부 같은 회사 소속 재직자 스냅샷에서
 * 갈리므로({@code deleted_at IS NULL}), 한 레코드 타입({@link MemberRow})으로 묶고 서비스가
 * 화면별로 재조립한다 — TeamService.buildContext 와 같은 이유다.
 */
public interface MemberDirectoryQueryPort {

    /** 회사 소속 재직자 전원(퇴사자 제외). 목록·조직도 조립용 — 필터·검색·페이징은 서비스가 한다. */
    List<MemberRow> findActiveByCompany(Long companyId);

    /** 상세 조회용. 다른 회사 소속이거나 없으면 empty — 컨트롤러까지 404 로 나간다(403 은 존재를 알려준다). */
    Optional<MemberRow> findActiveById(Long companyId, Long memberId);

    /** 이메일 중복 확인(§5-1 검증 3). 퇴사자의 이메일은 다시 쓸 수 있다 — deleted_at IS NULL 만 본다. */
    boolean existsActiveEmail(Long companyId, String email);

    /** 좌석 상한 판정용(§5-1 검증 5). 살아 있는 구독이 없으면 empty — FREE 로 둘러대지 않는다. */
    Optional<Plan> findActivePlan(Long companyId);

    /**
     * §5-1·§7-4 역할 지정. 화면이 {@code GET /api/teams} 로 받은 부서별 역할 목록에서 고른 id 를
     * 그대로 보내므로, 그 id 가 이 회사·이 부서의 것인지만 확인한다.
     *
     * <p>이름이 아니라 id 로 받는 이유: {@code role} 에는 (company_id, name) UNIQUE 가 없어
     * 같은 이름이 두 부서에 하나씩 있을 수 있다. 이름으로 되돌리면 화면이 고른 역할과 저장되는
     * 행이 갈린다 — id 로 받으면 해석 단계 자체가 없다.
     *
     * <p>{@code teamId} 를 같이 보는 이유: 역할은 부서에 매인 값이라(V2.3.8) 다른 부서의 역할을
     * 붙이면 조직도에서 그 사원이 자기 부서에 없는 역할로 묶인다. 부서에 매이지 않은 시스템
     * 역할("없음", V2.3.9)만 예외로 어느 부서에나 붙는다.
     */
    boolean existsAssignableRole(Long companyId, Long teamId, Long roleId);

    record MemberRow(
            Long memberId,
            String name,
            String email,
            Long teamId,
            String teamName,
            Long positionId,
            String positionName,
            Long roleId,
            String roleLabel,
            Authority authority,
            boolean isAdmin,
            MemberStatus status,
            LocalDate joinedOn,
            /** status 가 WAITING 일 때만 채워진다 — 어느 쪽 대기인지(§7-1). 그 외에는 null. */
            PendingHandoverType pendingType,
            /** status 가 VACATION 일 때만 채워진다 — 승인된 휴직의 시작일. 그 외에는 null. */
            LocalDate leaveStartDate,
            /** status 가 VACATION 일 때만 채워진다 — 승인된 휴직의 종료일. 그 외에는 null. */
            LocalDate leaveEndDate
    ) {
    }
}
