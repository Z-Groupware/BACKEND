package com.module06.backend.global.response;

import java.util.List;

/* comment.
    목록 조회 페이지네이션 공용 응답 봉투. project·action 목록 3개(GET /api/projects,
    GET /api/actions, GET /api/team/actions)가 공용으로 쓴다(2026-08-10, 이홍근 요청).
    새 파일이라 기존 global 파일을 건드리지 않는다 — ApiResponse와 같은 위치.
*/
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        // page를 먼저 long으로 넓혀야 한다 — page+1을 int로 먼저 계산하면 page가
        // Integer.MAX_VALUE일 때 오버플로로 음수가 되어 hasNext가 실제와 반대로 나온다
        // (CodeRabbit 지적, PR #305).
        boolean hasNext = ((long) page + 1) * size < totalElements;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
    }
}
