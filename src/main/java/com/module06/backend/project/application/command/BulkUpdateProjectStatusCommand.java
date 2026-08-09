package com.module06.backend.project.application.command;

import java.util.List;

import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    OWNER 프로젝트 보드 "저장" 버튼용 일괄 상태 변경 명령. all-or-nothing —
    항목 하나라도 권한/존재 검증에 실패하면 전체를 반영하지 않는다.
*/
public record BulkUpdateProjectStatusCommand(Long requesterId, List<Item> items) {

    public record Item(Long projectId, ProjectStatus status) {
    }
}
