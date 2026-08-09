package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort.ReviewAction;
import com.module06.backend.capture.application.result.ActionReview;
import com.module06.backend.capture.application.result.ActionReview.NeedsReview;
import com.module06.backend.capture.application.result.ActionReview.PersonActions;
import com.module06.backend.capture.application.usecase.GetActionReviewUseCase;

/*
 * RVW-01 · 액션 분배 검토 조회.
 *
 * 파이프라인 산출물을 **사람이 처음 보는 자리**다. L7 이 가른 두 묶음이 여기서 화면으로 나간다.
 *
 * <h2>회사 스코프 관문을 먼저 지난다</h2>
 * 조회 조건에 회사를 끼워 넣는 것만으로는 부족하다 — 언젠가 한 곳이 빠지고 그 API 만 조용히
 * 뚫린다(CAP-06 이 실제로 그랬다 · #100). 어댑터 쿼리에도 company_id 를 넣지만, 그건 두 번째
 * 방어선이고 첫 번째는 여기다.
 */
@Service
@RequiredArgsConstructor
public class ActionReviewService implements GetActionReviewUseCase {

    private final ActionReviewQueryPort actionReviewQueryPort;
    private final MeetingAccessGuard meetingAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public ActionReview getReview(long companyId, long meetingId, String reviewStatus) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        List<ReviewAction> actions = actionReviewQueryPort.findByMeeting(companyId, meetingId, reviewStatus);

        return new ActionReview(
                groupByPerson(actions),
                needsReviewOf(actions),
                /*
                 * 분배 시각. **null 이면 아직 아무 데도 가 있지 않다** — 자동 확정 건도
                 * 마찬가지이고, 화면의 「확정 전 검토 가능」이 그 뜻이다(명세 RVW-01).
                 *
                 * 회의 단위 값이라 액션 목록과 따로 읽는다. 확정 뒤에 추가된 액션(RVW-03)은
                 * 아직 안 나갔으므로 목록에는 나가지 않은 행이 섞여 있을 수 있다.
                 */
                actionReviewQueryPort.dispatchedAtOf(companyId, meetingId).orElse(null));
    }

    /*
     * 담당자별로 묶는다.
     *
     * 정렬은 어댑터가 (담당자, 액션 id)로 이미 해두므로 LinkedHashMap 이 그 순서를 유지한다 —
     * 여기서 다시 정렬하면 SQL 이 정한 순서와 갈릴 여지가 생긴다.
     *
     * **담당자가 null 인 묶음도 만든다.** 담당자 미정이거나 명단 밖을 가리킨 액션들인데,
     * 그 묶음을 버리면 화면에서 사라진다 — 담당자가 없다는 것이야말로 사람이 봐야 하는 상태다.
     */
    private List<PersonActions> groupByPerson(List<ReviewAction> actions) {
        Map<Long, PersonActions> grouped = new LinkedHashMap<>();
        for (ReviewAction action : actions) {
            PersonActions person = grouped.computeIfAbsent(
                    action.assigneeMemberId(),
                    memberId -> new PersonActions(memberId, action.assigneeName(), new ArrayList<>()));
            person.actions().add(action);
        }
        return List.copyOf(grouped.values());
    }

    /*
     * 검토가 필요한 건을 센다.
     *
     * **자동확정되지 않은 것 전부**다 — 게이트가 떨어뜨린 것(autoConfirmed=false)과 게이트를
     * 아예 안 지난 것(null · 수동 추가) 둘 다 사람이 봐야 하는 것은 같다. 둘을 나누면 화면이
     * 묶음을 셋으로 만들어야 하는데 명세는 둘로 나눈다.
     */
    private NeedsReview needsReviewOf(List<ReviewAction> actions) {
        List<Long> ids = actions.stream()
                .filter(action -> !Boolean.TRUE.equals(action.autoConfirmed()))
                .map(ReviewAction::actionId)
                .toList();
        return new NeedsReview(ids.size(), ids);
    }
}
