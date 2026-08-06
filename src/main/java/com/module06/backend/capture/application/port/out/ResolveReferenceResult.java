package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.ResolvedReference;

/* AI-02(L1.5 지시어 해소) 호출 결과. 회의당 한 번 나온다 — 문맥 전체를 봐야 선행사를 찾는다. */
public record ResolveReferenceResult(
        List<ResolvedReference> references,
        LayerRun run
) {
}
