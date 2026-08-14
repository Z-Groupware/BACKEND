package com.module06.backend.identity.member.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;

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

    /**
     * §7-4 역할 라벨 변경. 화면이 라벨을 id 가 아니라 이름으로 보내므로(역할 select 의 값이 곧
     * 이름이다) 이름을 회사 안에서 id 로 되돌린다. 없는 이름이면 empty — 호출자가 404 로 답한다.
     *
     * <p>여기서 역할을 새로 만들지 않는다. 역할 생성은 온보딩·부서 관리의 일이라, 오타 하나가
     * 조용히 새 역할을 만들면 역할 목록이 사람마다 다른 이름으로 불어난다.
     *
     * @param label 전 회사 공용 시스템 역할("없음")도 찾을 수 있다 — 그건 회사 소유가 아니다
     */
    Optional<Long> findRoleIdByLabel(Long companyId, String label);

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
