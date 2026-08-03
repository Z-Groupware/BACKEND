package com.module06.backend.project.presentation.api.response;

/* comment.
    프로젝트 상세(기획 탭) 응답 DTO. 기획(description)을 포함한다 — 전 구성원 공개다.
    첨부파일 목록을 인라인으로 담아서 FE가 다운로드 링크를 바로 그릴 수 있게 한다.
    담을 값: id·태그·색상·이름·기획·상태·마감일·지정 부서 목록·첨부파일 목록.

    연결된 클래스
    - ProjectController       : 이 DTO를 내보내는 진입점
    - GetProjectDetailService : 이 DTO를 만드는 구현체
    - AttachmentResponse      : 인라인으로 실리는 첨부파일 항목
*/
public record ProjectDetailResponse() {
}
