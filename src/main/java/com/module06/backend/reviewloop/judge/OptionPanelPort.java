package com.module06.backend.reviewloop.judge;

/**
 * 판단 패널 생성 seam — finding에 대해 처리 안건 N개 + 별도 추천을 만든다.
 * 사람은 "심판" 대신 "선택"만 한다(추천 참고). 테스트=stub, 런타임=GeminiOptionPanelAdapter.
 */
@FunctionalInterface
public interface OptionPanelPort {
    OptionPanel propose(Finding finding);
}
