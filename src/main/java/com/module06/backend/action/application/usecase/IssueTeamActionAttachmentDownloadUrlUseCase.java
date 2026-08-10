package com.module06.backend.action.application.usecase;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;

/* comment.
    FR-AC-06 후속(2026-08-10, 이홍근 요청) — 팀 액션 상세에 인라인으로 뜨는 첨부파일의 다운로드
    URL 발급. project.application.command.IssueAttachmentDownloadUrlUseCase와 같은 기능이지만
    진입 경로가 다르다(팀 액션 상세를 보는 사람 기준 IDOR 방지 — companyId+teamActionId로
    소유를 먼저 확인한 뒤에야 attachmentId를 본다).

    project·action이 같은 사람(C) 소유라 ACL 포트 없이 ProjectAttachmentStoragePort를
    TeamActionService가 직접 주입받아 쓴다(calendar가 project/action repository를 직접
    조합하는 것과 같은 전제, 사용자 확인 완료) — 그래서 반환 타입도 그 포트의 레코드를
    그대로 쓴다.
*/
public interface IssueTeamActionAttachmentDownloadUrlUseCase {

    IssuedDownloadUrl issueAttachmentDownloadUrl(Long companyId, Long teamActionId, Long attachmentId);
}
