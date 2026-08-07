package com.module06.backend.action.application.usecase;

import java.util.List;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.AttachmentReference;

/* comment.
    FR-AC-06 — 팀 액션 상세 조회 기능 계약. teamActionId가 전역 고유키라 전 구성원 공개다.
    소속 프로젝트의 첨부파일도 함께 포함해서 내려준다.
    회사 스코프는 companyId로 다시 확인한다 — 다른 회사 팀 액션 id를 넣으면 존재하지 않는 것과
    같은 404로 덮는다(GetActionDetailUseCase와 동일 판단, #100).
    ?tab=timeline(FR-AC-08, 하위 개인 액션 타임라인)은 아직 배선하지 않는다 — 별도 착수.

    연결된 클래스
    - ActionRepository            : 조회
    - ActionReferenceRepository   : 프로젝트태그·팀명·첨부파일 조인
    - TeamActionDetailResponse    : 출력 DTO (presentation)
    - TeamActionController        : 호출자 (presentation)
*/
public interface GetTeamActionDetailUseCase {

    TeamActionDetail getTeamActionDetail(Long companyId, Long teamActionId);

    record TeamActionDetail(
            Action action,
            String projectTag,
            String teamName,
            List<AttachmentReference> attachments
    ) {
    }
}
