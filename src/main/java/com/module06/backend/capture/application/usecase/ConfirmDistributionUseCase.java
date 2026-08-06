package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.application.result.DistributionConfirmed;

/*
 * RVW-05 · 액션 분배 확정.
 *
 * **파이프라인의 마지막 사람 손이다.** 이 호출 전까지 액션은 만들어져 있어도 아무 데도 가 있지
 * 않고(화면의 「확정 전 검토 가능」이 그 뜻이다), 이 호출 뒤에 각자의 보드에 카드가 생긴다.
 * 상태 변경·DnD·지연 표시는 그때부터 C(액션·보드) 소관이다.
 *
 * **되돌리기 어렵다.** 보드로 나간 액션을 회수하는 경로가 없다 — 그래서 구멍이 남은 채로는
 * 막고(409), 사람이 눈으로 보고 ?confirm=true 로만 강행하게 한다.
 */
public interface ConfirmDistributionUseCase {

    DistributionConfirmed confirm(ConfirmDistributionCommand command);

    /*
     * @param force ?confirm=true. 확인되지 않은 STT 구간이나 미검토 액션이 남아 있어도 강행한다.
     *              **강행해도 미검토 액션은 나가지 않는다** — 막힌 이유를 무시하는 것이지
     *              검토하지 않은 것을 확정하는 것이 아니다.
     */
    record ConfirmDistributionCommand(
            long companyId,
            long meetingId,
            long requestedBy,
            boolean force
    ) {
    }
}
