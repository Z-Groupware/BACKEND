package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingVocabularyJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingVocabularyRepository;

/* meeting_vocabulary 접근 어댑터다(STT-01 · STT-02). */
@Repository
@RequiredArgsConstructor
public class MeetingVocabularyPersistenceAdapter implements MeetingVocabularyRepository {

    private final SpringDataMeetingVocabularyRepository vocabularyRepository;

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
    @Override
    @Transactional
    public VocabularyView markRebuilding(long meetingId) {
        MeetingVocabularyJpaEntity entity = vocabularyRepository.findByMeetingId(meetingId)
                .orElseGet(() -> MeetingVocabularyJpaEntity.pending(meetingId));

        entity.markRebuilding();
        return toView(vocabularyRepository.save(entity));
    }

    @Override
    @Transactional
    public void assignProviderName(long vocabularyId, String providerVocabularyName) {
        vocabularyRepository.findById(vocabularyId)
                .ifPresent(entity -> {
                    entity.assignProviderName(providerVocabularyName);
                    vocabularyRepository.save(entity);
                });
    }

    private static VocabularyView toView(MeetingVocabularyJpaEntity entity) {
        return new VocabularyView(
                entity.getId(),
                entity.getMeetingId(),
                entity.getStatus(),
                entity.getPhraseCount(),
                entity.getProviderVocabularyName(),
                entity.getBuiltAt());
    }
}
