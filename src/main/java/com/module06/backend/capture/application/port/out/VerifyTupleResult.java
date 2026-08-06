package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.VerifyVerdict;

/*
 * AI-07(L5 관점 다변화 검증) 호출 결과. tuple 하나마다 한 번 나온다.
 *
 * <h2>agree 는 "맞다/틀리다"가 아니다</h2>
 * 두 관점(EXTRACT_NARROW · VERIFY)이 **같은 말을 했는가**이다. false 는 tuple 이 틀렸다는
 * 판정이 아니라 "확신할 수 없다"이고, 그래서 이 값으로 tuple 을 지우지 않는다 — 사람이 볼
 * 대상으로 표시할 뿐이다. 앙상블의 가치는 정답 선택이 아니라 불확실성 탐지다(명세 AI-07).
 *
 * 한 관점만 실패해도 false 다. 실패한 관점을 "동의"로 세면 검증이 반쪽만 돌았는데
 * 자동 확정으로 나간다.
 *
 * @param disagreementFields 갈린 필드명. 명세가 정한 이름을 그대로 쓴다
 *                           (title · assigneeCandidatePersonId · dueDate). 좁은 시야에서
 *                           tuple 자체가 재현되지 않으면 notReproduced 하나가 온다 —
 *                           필드가 갈린 것과 "아예 안 나왔다"는 다른 신호라 값을 나눠 둔다.
 * @param verdict            VERIFY 관점 한쪽의 판정. null 이면 그 관점이 실패한 것이다.
 *                           agree 와 어긋날 수 있다(VerifyVerdict 주석).
 * @param reason             VERIFY 관점이 그렇게 판정한 근거. 게이트를 조일지 풀지 판단할
 *                           재료라 버리지 않는다.
 */
public record VerifyTupleResult(
        boolean agree,
        List<String> disagreementFields,
        VerifyVerdict verdict,
        String reason,
        LayerRun run
) {
}
