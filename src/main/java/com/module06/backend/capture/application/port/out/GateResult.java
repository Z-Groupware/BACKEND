package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.GateVerdict;

/*
 * AI-05(L3.5 확정/논의 게이트) 호출 결과. 주제마다 한 번 나온다.
 *
 * 판정이 없는 항목이 있을 수 있다. Python 은 precision 우선으로 애매한 항목을 DISCUSSED 로
 * 채우지만, 요청 항목 수와 응답 판정 수가 반드시 같다고 가정하지 않는다 — 가정하고 인덱스로
 * 맞추면 하나가 빠졌을 때 그 뒤 항목 전체의 판정이 한 칸씩 밀린다. 그래서 itemKey 로 되짚는다.
 */
public record GateResult(
        List<GateVerdict> verdicts,
        LayerRun run
) {
}
