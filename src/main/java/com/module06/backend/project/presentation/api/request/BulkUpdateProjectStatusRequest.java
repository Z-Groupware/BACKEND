package com.module06.backend.project.presentation.api.request;

import java.util.List;

import com.module06.backend.project.domain.model.ProjectStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/* comment.
    보드 "저장" 버튼용 일괄 상태 변경 요청 DTO.
    형태: { "items": [ { "projectId": 1, "status": "DONE" }, ... ] }
    items는 비어있을 수 없고, 한 항목이라도 실패하면 전체가 실패한다(all-or-nothing).

    연결된 클래스
    - ProjectController               : 이 DTO를 받는 진입점
    - BulkUpdateProjectStatusCommand  : 이 DTO가 변환되는 application 명령
    - ProjectStatus                   : items의 status 값
*/
public record BulkUpdateProjectStatusRequest(
        @NotEmpty List<@Valid Item> items
) {

    public record Item(
            @NotNull Long projectId,
            @NotNull ProjectStatus status
    ) {
    }
}
