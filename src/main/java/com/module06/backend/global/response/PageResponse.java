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
        boolean hasNext = (long) (page + 1) * size < totalElements;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
    }
}
