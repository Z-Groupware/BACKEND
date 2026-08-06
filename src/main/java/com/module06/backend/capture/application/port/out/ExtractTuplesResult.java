package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.AssignmentTuple;

/* AI-06(L4 assignment tuple 추출) 호출 결과. 주제마다 한 번 나온다(명세 「세그먼트별 호출」). */
public record ExtractTuplesResult(
        List<AssignmentTuple> tuples,
        LayerRun run
) {
}
