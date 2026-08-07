package com.module06.backend.action.infrastructure.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.capture.application.port.out.ReviewActionDeletePort;
import com.module06.backend.global.exception.BusinessException;

/* comment.
    action(C)이 구현하는 검토(A) 아웃바운드 포트. A가 정의한 ReviewActionDeletePort
    (capture.application.port.out)를 실제로 배선한다 — ReviewActionCreateAdapter ·
    ActionReviewApplyAdapter와 같은 방향이다(2026-08-07, RVW-04 착수).

    is_manual을 여기서도 확인한다. A가 조회 결과로 이미 막지만(409 MEETING_409_7), 이 포트는
    공개된 인바운드 경계라 한 곳이 빠지면 그 경로만 조용히 뚫린다. 특히 이 메서드는 되돌릴 수
    없는 삭제이고, AI 액션을 지우면 review_log에 남길 판정 대상 자체가 사라진다.

    회사 스코프도 다시 본다 — ActionReviewApplyAdapter와 같은 이유다(#100).

    연결된 클래스
    - ReviewActionDeletePort : 구현하는 계약 (capture.application.port.out)
    - Action                 : is_manual 판정 대상
    - ActionRepository       : 조회·삭제 위임 대상
*/
@Component
@RequiredArgsConstructor
public class ReviewActionDeleteAdapter implements ReviewActionDeletePort {

    private final ActionRepository actionRepository;

    @Override
    @Transactional
    public void deleteManual(long companyId, long actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        /*
         * 다른 회사 액션도 미존재와 같은 응답으로 덮는다. 403·500을 주면 "그 액션은 존재한다"가
         * 새어 나가고 id를 훑어 남의 회사 액션 개수를 셀 수 있다.
         */
        if (!Long.valueOf(companyId).equals(action.getCompanyId())) {
            throw new BusinessException(ActionErrorCode.ACTION_NOT_FOUND);
        }

        /*
         * AI 생성 액션은 지우지 않는다. A가 이미 막았지만 여기서도 본다 — 이 경계를 지나면
         * 라벨이 사라지고, 지나간 회의는 다시 만들 수 없어 되돌릴 방법이 없다.
         */
        if (!action.isManual()) {
            throw new BusinessException(ActionErrorCode.ACTION_DELETE_NOT_MANUAL);
        }

        actionRepository.delete(action);
    }
}
