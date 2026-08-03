package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;

/* comment.
    FR-PJ-06 프로젝트 상태 일괄 변경 구현체. 쓰기 트랜잭션 경계를 가진다.
    항목 하나라도 권한·검증에 실패하면 전체를 롤백한다(all-or-nothing).
    부분 성공을 허용하면 FE 보드 상태와 DB가 어긋난 채로 남는다.

    연결된 클래스
    - BulkUpdateProjectStatusUseCase : 구현하는 계약
    - BulkUpdateProjectStatusCommand : 입력
    - ProjectOwnerOnlyPolicy         : 항목별 권한 검사
    - ProjectRepository              : 조회·저장
*/
public class BulkUpdateProjectStatusService implements BulkUpdateProjectStatusUseCase {
}
