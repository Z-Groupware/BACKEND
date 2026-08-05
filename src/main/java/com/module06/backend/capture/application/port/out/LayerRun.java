package com.module06.backend.capture.application.port.out;

/*
 * 계층 호출 한 번의 "무엇으로 만들어졌는지"다. 산출물과 함께 반드시 올라온다.
 *
 * 토큰은 선택 항목이 아니다(V5.6 주석 ③ · 명세 AI-06). 회의당 계층별 토큰을 기록해야
 * QLTY-03(비용)이 성립하고 특화 모델 전환의 손익분기점을 계산할 수 있다. 나중에 붙이면
 * 그때까지의 데이터가 없다.
 *
 * modelName·promptVersion 이 없으면 프롬프트를 바꾼 뒤 정확도 변화를 무엇 탓으로 돌릴지
 * 알 수 없다 — 모델을 바꾼 것인지 프롬프트를 바꾼 것인지 구분되지 않는다.
 *
 * 주제마다 L3 를 N 번 부르므로 토큰은 누적해서 더한다(plus).
 */
public record LayerRun(
        int tokensIn,
        int tokensOut,
        String modelName,
        String promptVersion
) {

    public static LayerRun empty() {
        return new LayerRun(0, 0, null, null);
    }

    /*
     * 같은 계층의 여러 호출을 합친다.
     *
     * 모델·프롬프트 버전은 **나중 것으로 덮지 않고 먼저 것을 유지**한다. 한 계층 안에서
     * 둘이 달라지는 일은 없어야 하고, 만약 달라진다면 그건 배포 중 교체 같은 사고다 —
     * 마지막 값으로 덮으면 그 사고가 기록에서 사라진다.
     */
    public LayerRun plus(LayerRun other) {
        return new LayerRun(
                tokensIn + other.tokensIn(),
                tokensOut + other.tokensOut(),
                modelName != null ? modelName : other.modelName(),
                promptVersion != null ? promptVersion : other.promptVersion()
        );
    }
}
