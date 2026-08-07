package com.module06.backend.capture.application.port.out;

/*
 * 사람이 직접 추가한 액션을 지우는 아웃바운드 포트다(RVW-04). A 가 선언하고 C(액션)가 배선한다 —
 * {@link ReviewActionCreatePort} · {@link ActionReviewApplyPort} 와 같은 방향이다.
 *
 * <h2>왜 지우는 포트가 따로 있나</h2>
 * 액션을 지우는 것은 이 경로 하나뿐이고, 그 조건(수동 추가 건일 것)이 검토 화면의 규칙이다.
 * 범용 delete 를 열어두면 AI 액션을 지우는 경로가 함께 생기는데, 그건 라벨을 지우는 일이라
 * **이 도메인에서 가장 되돌릴 수 없는 손실**이다.
 */
public interface ReviewActionDeletePort {

    /*
     * @param companyId 회사 스코프를 어댑터에서 한 번 더 본다. 호출 전에 이미 걸러오지만,
     *                  이 포트는 공개된 경계라 한 곳이 빠지면 그 경로만 조용히 뚫린다(#100).
     */
    void deleteManual(long companyId, long actionId);
}
