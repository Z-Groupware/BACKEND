package com.module06.backend.reviewloop.judge;

/** 한 라운드 리뷰 → verdict. ReviewLoop가 구현하고, ReviewRunner가 반복 호출한다(테스트는 람다로 대체). */
@FunctionalInterface
public interface RoundReviewer {
    JudgeVerdict review(String filePath, String code);
}
