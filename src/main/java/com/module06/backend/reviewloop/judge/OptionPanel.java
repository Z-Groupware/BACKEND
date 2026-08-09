package com.module06.backend.reviewloop.judge;

import java.util.List;

/** 판단 패널 — 동등한 안건 목록 + 별도 추천. 사람은 이 중 하나를 최종 선택한다. */
public record OptionPanel(
        List<PanelOption> options,
        Recommendation recommendation
) {}
