package com.module06.backend.capture.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;

/*
 * RVW-01 응답의 재료다.
 *
 * <h2>사람별로 묶는 이유</h2>
 * 검토 화면이 "누가 무엇을 받게 되는가"를 보여주는 화면이라, 담당자가 1차 축이다.
 * 액션 목록을 평평하게 주면 화면이 다시 묶어야 하고, 그 묶기가 프론트마다 갈린다.
 *
 * <h2>needsReview 를 따로 세는 이유</h2>
 * 화면 상단에 "확인 필요 N건"을 띄우려면 목록을 훑기 전에 수가 필요하다. actionIds 까지
 * 주는 것은 화면이 그 묶음으로 스크롤·필터할 수 있게 하기 위해서다.
 *
 * @param dispatchedAt 분배된 시각. **아직 항상 null 이다** — 분배는 RVW-05 가 하고 그 API 는
 *                     붙지 않았다. 자동 확정 건도 분배 전까지는 아무 데도 가 있지 않다는
 *                     뜻이 이 필드로 드러난다(명세 RVW-01).
 */
public record ActionReview(
        List<PersonActions> actionsByPerson,
        NeedsReview needsReview,
        LocalDateTime dispatchedAt
) {

    /*
     * 담당자 한 명과 그에게 배정된 액션들.
     *
     * memberId 가 null 인 묶음이 나올 수 있다 — 담당자가 정해지지 않았거나 명단 밖을
     * 가리킨 액션들이다. 그 묶음을 버리지 않는다. **담당자가 없다는 것이야말로 사람이
     * 봐야 하는 상태다.**
     */
    public record PersonActions(Long memberId, String name, List<ReviewAction> actions) {
    }

    /*
     * 검토가 필요한 건수와 그 id 들.
     *
     * "자동확정되지 않은 것"으로 센다 — 게이트가 떨어뜨린 것과 아직 게이트를 안 지난 것
     * (수동 추가) 둘 다 사람이 봐야 하는 것은 같다.
     */
    public record NeedsReview(int count, List<Long> actionIds) {
    }
}
