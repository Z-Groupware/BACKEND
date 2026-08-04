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
        this.status = status;
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
            LocalDate dueDate,
            Long createdBy,
            List<Long> teamIds
    ) {
        return new Project(
                null, companyId, tag, name, description, color,
                ProjectStatus.TODO, dueDate, createdBy, teamIds,
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
            LocalDate dueDate,
            Long createdBy,
            List<Long> teamIds,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Project(
                id, companyId, tag, name, description, color,
                status, dueDate, createdBy, teamIds,
                deletedAt, createdAt, updatedAt
        );
    }

    // tag는 파라미터로 받지 않는다 — FR-PJ-04 강제.
    public void update(String name, String description, String color, LocalDate dueDate, List<Long> teamIds) {
        this.name = name;
        this.description = description;
        this.color = color;
        this.dueDate = dueDate;
        this.teamIds.clear();
        this.teamIds.addAll(teamIds);
    }

    public void changeStatus(ProjectStatus status) {
        this.status = status;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.createdBy != null && this.createdBy.equals(memberId);
    }

    public List<Long> getTeamIds() {
        return Collections.unmodifiableList(teamIds);
    }
}
