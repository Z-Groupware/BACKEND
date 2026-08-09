package com.module06.backend.reviewloop.judge;

import java.util.List;

/**
 * 자동수정 seam — findings를 받아 코드를 고쳐 반환한다(고친 전체 코드).
 * 테스트=stub, 런타임=GeminiCodeFixerAdapter(또는 Claude). Judge(찾기)와 Fixer(고치기)는 분리된 역할.
 *
 * <p><b>seam은 유지한다</b>(review-loop/UNIFIED_DESIGN.md §3.4) — 구현은 휴면이지만 무인 모드 재개 시 필요하다.
 * 통합 루프의 실제 fixer는 이 인터페이스를 구현하지 않는다: 드라이버(Claude Code)가 Edit 도구로 직접 고친다.
 */
@FunctionalInterface
public interface CodeFixerPort {
    String fix(String filePath, String code, List<Finding> findings);
}
