package com.module06.backend.capture.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.domain.model.ReviewTargetType;
import com.module06.backend.capture.infrastructure.persistence.entity.ReviewLogJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataReviewLogRepository;

/*
 * 사람 라벨을 남기는 어댑터다.
 *
 * 트랜잭션을 새로 열지 않는다(REQUIRES_NEW 아님). 호출자(RVW-02)의 트랜잭션에 참여해야
 * **판정이 롤백되면 라벨도 함께 사라진다** — 반려되지 않은 액션에 "반려했다"는 라벨이 남으면
 * 그 행은 오답 사례로 학습되고, 지우려면 어느 행이 잘못됐는지 사람이 찾아야 한다.
 */
@Repository
@RequiredArgsConstructor
public class ReviewLogPersistenceAdapter implements ReviewLogRepository {

    private final SpringDataReviewLogRepository reviewLogRepository;

    @Override
    @Transactional
    public long append(ReviewLogEntry entry) {
        ReviewLogJpaEntity saved = reviewLogRepository.save(ReviewLogJpaEntity.of(
                entry.companyId(),
                entry.meetingId(),
                // 액션(RVW-02)과 요약 항목(ANLZ-04)이 같은 표를 쓴다. 사유 코드 규칙만
                // 다르고(V5.9 CHECK), 그 판정은 부르는 쪽이 이미 마쳤다.
                entry.targetType(),
                entry.targetId(),
                // enum 이름이 아니라 전송값이다 — "L1_5" 를 저장하면 파이썬 쪽 "L1.5" 와 갈린다.
                entry.layer().wireValue(),
                entry.decision(),
                entry.rejectReason(),
                entry.inputContext(),
                entry.llmOutput(),
                entry.humanValue(),
                entry.confirmedBy(),
                entry.modelName(),
                entry.promptVersion(),
                entry.manual()));
        return saved.getId();
    }
}
