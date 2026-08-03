package com.module06.backend.reviewloop.judge;

/** 판단 안건 하나 — 동등하게 나열된다(추천 표시는 여기 없음, Recommendation이 따로 가짐). */
public record PanelOption(
        String letter,      // "A" / "B" / "C"
        PanelAction action,
        String title,
        String rationale
) {}
