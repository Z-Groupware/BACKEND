package com.module06.backend.capture.domain.model;

/*
 * L1.5 가 푼 지시어 하나다.
 *
 * surface 는 발화 안에 **실제로 나타난** 지시 표현 원문이다("그거"·"아까 그건").
 * Python 후처리가 발화 텍스트에 이 문자열이 있는지 확인하고 없으면 버리므로, 여기 올라온
 * 값은 원문에 존재한다고 봐도 된다. 그래도 이쪽에서 치환에 쓸 때는 다시 확인한다 —
 * 계약이 갈렸을 때 엉뚱한 자리에 주석이 붙는 것보다 안 붙는 편이 낫다.
 *
 * evidenceUtteranceId 는 선행사가 있는 발화다. 근거 강제 — 이 값이 없는 항목은 계층이
 * 애초에 반환하지 않는다.
 */
public record ResolvedReference(
        Long utteranceId,
        String surface,
        ReferenceType referenceType,
        Long resolvedPersonId,
        String resolvedText,
        Long evidenceUtteranceId
) {

    /*
     * 이 해소 결과를 발화에 주석으로 붙일 수 있는가.
     *
     * UNRESOLVED 는 붙이지 않는다 — "모르겠다"를 발화에 적어 넣으면 뒤 계층이 그 문구를
     * 내용으로 인용한다. 대상이 비어 있는 항목도 같다.
     */
    public boolean isAnnotatable() {
        return referenceType != null
                && referenceType != ReferenceType.UNRESOLVED
                && surface != null && !surface.isBlank()
                && (resolvedPersonId != null || (resolvedText != null && !resolvedText.isBlank()));
    }
}
