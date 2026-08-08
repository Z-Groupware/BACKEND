package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.QualityGoldSetRepository;
import com.module06.backend.capture.infrastructure.persistence.entity.QualityGoldSetJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataQualityGoldSetRepository;

/*
 * quality_gold_set 접근 어댑터다(QLTY-01).
 *
 * 기존 행을 읽어 고치는 경로가 없다 — 동결된 정답지를 수정하면 그걸로 잰 이전 측정치가 전부
 * 무의미해진다. 재라벨링은 언제나 새 버전 INSERT 다(V5.11 주석).
 */
@Repository
@RequiredArgsConstructor
public class QualityGoldSetPersistenceAdapter implements QualityGoldSetRepository {

    private final SpringDataQualityGoldSetRepository goldSetRepository;

    /*
     * ⚠ 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST)
     * 타입으로 주입된다. 캡처 전용 Clock 빈을 새로 만들면 안 된다.
     */
    private final Clock clock;

    /*
     * **트랜잭션을 두지 않는다.** UNIQUE(meeting_id, version) 충돌은 커밋 시점에 나오므로,
     * 여기서 감싸면 호출자가 그 예외를 잡아도 이미 rollback-only 라 커밋에서 다시 터진다
     * (AnalysisRunSequenceIssuer 가 같은 이유로 갈라져 있다). save 가 자기 트랜잭션에서 돌면
     * 위반이 그대로 호출자에게 올라가 409 로 옮겨진다.
     */
    @Override
    public GoldSetView freeze(FreezeCommand command) {
        QualityGoldSetJpaEntity saved = goldSetRepository.save(QualityGoldSetJpaEntity.frozen(
                command.companyId(),
                command.meetingId(),
                command.version(),
                command.labeledActions(),
                command.labeledItems(),
                command.frozenBy(),
                command.note(),
                LocalDateTime.now(clock)));

        return new GoldSetView(saved.getId(), saved.getVersion(), 0, saved.getFrozenAt());
    }

    @Override
    @Transactional(readOnly = true)
    public int latestVersionOf(long meetingId) {
        return goldSetRepository.findTopByMeetingIdOrderByVersionDesc(meetingId)
                .map(QualityGoldSetJpaEntity::getVersion)
                // 아직 없다. 다음 등록이 1 번이 된다(CHECK version >= 1).
                .orElse(0);
    }
}
