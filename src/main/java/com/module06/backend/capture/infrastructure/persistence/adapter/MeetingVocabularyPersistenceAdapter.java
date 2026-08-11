package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingVocabularyJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingVocabularyRepository;

/* meeting_vocabulary 접근 어댑터다(STT-01 · STT-02). */
@Repository
@RequiredArgsConstructor
public class MeetingVocabularyPersistenceAdapter implements MeetingVocabularyRepository {

    private final SpringDataMeetingVocabularyRepository vocabularyRepository;

    /* 승격·정리 시각을 찍는다. 프로젝트 전체에 Clock 빈이 하나뿐이라 타입으로 주입된다. */
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<VocabularyView> findByMeeting(long meetingId) {
        return vocabularyRepository.findByMeetingId(meetingId)
                .map(MeetingVocabularyPersistenceAdapter::toView);
    }

    /*
     * 없으면 만들고 있으면 되돌린다.
     *
     * 새로 만드는 경로가 필요한 이유 — **회의 예약 시점의 자동 생성이 아직 없다.** 그래서
     * 대부분의 회의에 행이 없고, 재생성(STT-02)이 사실상 첫 생성이다. 여기서 만들지 않으면
     * 사람이 버튼을 눌러도 아무 기록이 안 남는다.
     */
    /*
     * 재생성을 선점한다.
     *
     * **쓰기 잠금을 걸고 읽는다.** 잠금 없이 읽으면 동시 요청이 둘 다 "PENDING 아님"을 보고
     * 둘 다 선점에 성공해, 제공자에 어휘가 두 벌 만들어진다 — 계정 상한을 그만큼 갉아먹는다
     * (CodeRabbit PR #241). 행 잠금이 그 구간을 직렬화한다.
     *
     * 행이 아직 없을 때는 잠글 대상이 없으므로 UNIQUE(meeting_id) 가 대신 막는다. 진 쪽은
     * 커밋에서 제약 위반으로 떨어지고, 그건 곧 "다른 요청이 먼저 선점했다"는 뜻이다.
     */
    @Override
    @Transactional
    public Optional<VocabularyView> claimRebuild(long meetingId) {
        MeetingVocabularyJpaEntity entity = vocabularyRepository.findWithLockByMeetingId(meetingId)
                .orElseGet(() -> MeetingVocabularyJpaEntity.pending(meetingId));

        if (!entity.claimRebuild()) {
            // 이미 만드는 중이다. 여기서 또 제출하면 리소스가 중복 생성된다.
            return Optional.empty();
        }
        return Optional.of(toView(vocabularyRepository.save(entity)));
    }

    @Override
    @Transactional
    public void assignPendingName(long vocabularyId, String pendingVocabularyName) {
        vocabularyRepository.findById(vocabularyId)
                .ifPresent(entity -> {
                    entity.assignPendingName(pendingVocabularyName);
                    vocabularyRepository.save(entity);
                });
    }

    @Override
    @Transactional
    public void markBuildFailed(long vocabularyId, String errorCode) {
        vocabularyRepository.findById(vocabularyId)
                .ifPresent(entity -> {
                    entity.markBuildFailed(errorCode);
                    vocabularyRepository.save(entity);
                });
    }

    /*
     * 승격은 **쓰기 잠금 안에서** 한다. 폴링과 재생성(STT-02)이 같은 행을 동시에 만질 수 있고,
     * 사람이 방금 재생성을 눌러 대기 이름이 새 것으로 바뀐 뒤에 옛 폴링이 승격하면 **만들어지지도
     * 않은 리소스가 활성이 된다.**
     */
    @Override
    @Transactional
    public Optional<String> promoteToReady(long vocabularyId, int builtPhraseCount) {
        return vocabularyRepository.findById(vocabularyId)
                .flatMap(entity -> {
                    String previousActive = entity.promoteToReady(builtPhraseCount, LocalDateTime.now(clock));
                    vocabularyRepository.save(entity);
                    return Optional.ofNullable(previousActive);
                });
    }

    @Override
    @Transactional
    public void markCleaned(long vocabularyId) {
        vocabularyRepository.findById(vocabularyId)
                .ifPresent(entity -> {
                    entity.markCleaned(LocalDateTime.now(clock));
                    vocabularyRepository.save(entity);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<VocabularyView> findPendingBuilds(int limit) {
        return vocabularyRepository
                .findByStatusAndPendingVocabularyNameIsNotNullOrderByIdAsc(
                        VocabularyStatus.PENDING, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(MeetingVocabularyPersistenceAdapter::toView)
                .toList();
    }

    /*
     * 정리 대상 — 아직 안 지웠고(deleted_at IS NULL) 끝난 어휘.
     *
     * 활성 이름이 없는 행은 담지 않는다. 지울 리소스가 없는데 정리 대상으로 돌려주면 호출자가
     * 매 주기 같은 행을 집어 아무것도 못 하고 돌아온다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<VocabularyView> findCleanupTargets(int limit) {
        return vocabularyRepository
                .findByDeletedAtIsNullAndStatusInAndProviderVocabularyNameIsNotNullOrderByIdAsc(
                        EnumSet.of(VocabularyStatus.READY, VocabularyStatus.FAILED),
                        PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(MeetingVocabularyPersistenceAdapter::toView)
                .toList();
    }

    private static VocabularyView toView(MeetingVocabularyJpaEntity entity) {
        return new VocabularyView(
                entity.getId(),
                entity.getMeetingId(),
                entity.getStatus(),
                entity.getPhraseCount(),
                entity.getProviderVocabularyName(),
                entity.getBuiltAt(),
                entity.getPendingVocabularyName(),
                entity.getDeletedAt() != null);
    }
}
