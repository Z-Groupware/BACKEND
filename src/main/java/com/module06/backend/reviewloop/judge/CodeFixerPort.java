package com.module06.backend.reviewloop.judge;

import java.util.List;

/**
 * 자동수정 seam — findings를 받아 코드를 고쳐 반환한다(고친 전체 코드).
 * 테스트=stub, 런타임=GeminiCodeFixerAdapter(또는 Claude). Judge(찾기)와 Fixer(고치기)는 분리된 역할.
 */
@FunctionalInterface
public interface CodeFixerPort {
    String fix(String filePath, String code, List<Finding> findings);
}
