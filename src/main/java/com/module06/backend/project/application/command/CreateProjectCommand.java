package com.module06.backend.project.application.command;

import java.time.LocalDate;
import java.util.List;

/* comment.
    프로젝트 생성 입력값. companyId·createdBy는 요청 바디가 아니라 액세스 토큰에서 온다 —
    Controller가 @AuthenticationPrincipal 로 꺼내 채워 넘긴다.

    옛 주석은 X-Company-Id/X-Member-Id 헤더에서 온다고 적혀 있었다. 그 헤더 브리지는
    2026-08-05 에 제거했다 — 헤더로 받으면 로그인만 한 사람이 남의 회사 번호를 적어 보낼 수 있어서,
    인증을 걸어도 막히지 않는 구멍이었다.
*/
public record CreateProjectCommand(
        Long companyId,
        Long createdBy,
        String tag,
        String name,
        String description,
        String color,
        LocalDate dueDate,
        List<Long> teamIds
) {
}
