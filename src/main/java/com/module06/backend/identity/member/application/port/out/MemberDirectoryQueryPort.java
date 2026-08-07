package com.module06.backend.identity.member.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
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

    record MemberRow(
            Long memberId,
            String name,
            String email,
            Long teamId,
            String teamName,
            Long positionId,
            String positionName,
            String roleLabel,
            Authority authority,
            boolean isAdmin,
            MemberStatus status,
            LocalDate joinedOn
    ) {
    }
}
