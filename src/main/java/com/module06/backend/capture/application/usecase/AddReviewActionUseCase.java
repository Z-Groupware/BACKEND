package com.module06.backend.capture.application.usecase;

import java.time.LocalDate;

import com.module06.backend.capture.application.result.ReviewActionAdded;

/*
 * RVW-03 · 액션 직접 추가.
 *
 * **AI 가 놓친 것을 사람이 넣는 자리다.** 파이프라인이 못 뽑은 할 일은 검토 화면에 아예 나타나지
 * 않으므로, 이 경로가 없으면 사람은 빠진 것을 알아도 넣을 방법이 없다.
 */
public interface AddReviewActionUseCase {

    ReviewActionAdded add(AddReviewActionCommand command);

    /*
     * @param assigneeMemberId **필수다.** 수동 추가 경로는 담당자를 강제한다 —
     *                         C 도메인과 2026-08-07 에 그렇게 나눴다. AI 분배 경로만 담당자
     *                         미정을 허용하고(사람이 검토 화면에서 고른다), 수동 추가는 사람이
     *                         이미 화면 앞에 있으므로 그 자리에서 정하는 것이 맞다.
     * @param evidenceTranscriptId 근거 발화. 사람이 원문에서 집어 넣었으면 붙고, 아니면 null 이다.
     */
    record AddReviewActionCommand(
            long companyId,
            long meetingId,
            Long assigneeMemberId,
            String title,
            String detail,
            LocalDate dueDate,
            Long evidenceTranscriptId,
            long requestedBy
    ) {
    }
}
