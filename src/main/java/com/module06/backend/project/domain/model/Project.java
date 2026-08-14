package com.module06.backend.project.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/* comment.
    프로젝트 애그리거트 루트. 태그·이름·기획·색상·마감일·상태·지정 부서(teamIds)를 보유하며,
    태그 불변(FR-PJ-04)은 update()가 tag를 파라미터로 받지 않는 방식으로 강제한다.
*/
@Getter
public class Project {

    private final Long id;
    private final Long companyId;
    private final String tag;
    private String name;
    private String description;
    private String color;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private final Long createdBy;
    private final List<Long> teamIds;
    private final LocalDateTime deletedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Project(
            Long id,
            Long companyId,
            String tag,
            String name,
            String description,
            String color,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate dueDate,
            Long createdBy,
            List<Long> teamIds,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.color = color;
        this.startDate = startDate;
        this.status = deriveStatus(status, startDate, LocalDate.now());
        this.dueDate = dueDate;
        this.createdBy = createdBy;
        this.teamIds = new ArrayList<>(teamIds);
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 생성. id·상태·타임스탬프는 저장소가 채운다.
    public static Project create(
            Long companyId,
            String tag,
            String name,
            String description,
            String color,
            LocalDate startDate,
            LocalDate dueDate,
            Long createdBy,
            List<Long> teamIds
    ) {
        return new Project(
                null, companyId, tag, name, description, color,
                ProjectStatus.TODO, startDate, dueDate, createdBy, teamIds,
                null, null, null
        );
    }

    // 저장소가 조회 결과를 이 애그리거트로 복원할 때 사용.
    public static Project reconstitute(
            Long id,
            Long companyId,
            String tag,
            String name,
            String description,
            String color,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate dueDate,
            Long createdBy,
            List<Long> teamIds,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Project(
                id, companyId, tag, name, description, color,
                status, startDate, dueDate, createdBy, teamIds,
                deletedAt, createdAt, updatedAt
        );
    }

    // tag는 파라미터로 받지 않는다 — FR-PJ-04 강제.
    public void update(String name, String description, String color, LocalDate startDate, LocalDate dueDate, List<Long> teamIds) {
        this.name = name;
        this.description = description;
        this.color = color;
        this.startDate = startDate;
        this.status = deriveStatus(this.status, startDate, LocalDate.now());
        this.dueDate = dueDate;
        this.teamIds.clear();
        this.teamIds.addAll(teamIds);
    }

    /*
     * 보드 상태변경(이슈 #497). 2026-08-10엔 "startDate는 표시값일 뿐 status엔 영향 없다"로
     * 갔었는데(커밋 8abbae3, 이홍근 요청) — 그때는 보드 화면 하나만 이 값을 쓴다고 가정했지만,
     * 목록 API도 같은 값을 신뢰해야 하는 두 번째 소비자로 붙으면서 "저장된 status를 그대로
     * 믿는 소비자"와 "매번 계산하는 소비자"가 갈라지는 문제가 드러나 2026-08-14 정반대로
     * 뒤집었다(이홍근 재확인). ProjectStatus.java의 "전환 제약 없음, 단계 건너뛰기·되돌리기
     * 모두 허용" 정책은 그대로 유지한다 — Action처럼 start()/complete()/reopen() 셋으로
     * 나눠 IllegalStateException으로 막지 않는다.
     */
    public void changeStatus(ProjectStatus targetStatus, LocalDate today) {
        switch (targetStatus) {
            case TODO -> this.startDate = null;
            case IN_PROGRESS -> {
                if (this.startDate == null || this.startDate.isAfter(today)) {
                    this.startDate = today;
                }
            }
            case DONE -> {
            }
        }
        this.status = targetStatus;
    }

    // DONE은 사람이 직접 지정할 때만 세팅되는 값이라 그대로 신뢰한다 — 그 외엔 startDate와
    // 오늘을 비교해 매번 다시 계산한다. project 테이블엔 is_done 컬럼이 없어(action과 달리)
    // 저장된 status==DONE 자체를 완료 신호로 재사용한다.
    private static ProjectStatus deriveStatus(ProjectStatus storedStatus, LocalDate startDate, LocalDate today) {
        if (storedStatus == ProjectStatus.DONE) {
            return ProjectStatus.DONE;
        }
        return (startDate == null || today.isBefore(startDate)) ? ProjectStatus.TODO : ProjectStatus.IN_PROGRESS;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.createdBy != null && this.createdBy.equals(memberId);
    }

    public List<Long> getTeamIds() {
        return Collections.unmodifiableList(teamIds);
    }
}
