package com.module06.backend.notice.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;
import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;

/* NOTI-03 공지 저장 계약을 Spring Data JPA로 구현하는 명령 어댑터다. */
@Component
@RequiredArgsConstructor
public class NoticeCommandPersistenceAdapter implements NoticeCommandRepository {

    /* notice 테이블에 신규 공지 행을 저장하는 기술 저장소다. */
    private final SpringDataNoticeRepository springDataNoticeRepository;

    /* 신규 공지를 JPA 엔티티로 저장하고 생성 식별자·시각이 반영된 도메인 모델을 반환한다. */
    @Override
    public Notice save(Notice notice) {
        /* 도메인과 영속성 매핑을 엔티티 변환 메서드에 위임하고 저장 결과를 다시 도메인으로 복원한다. */
        return springDataNoticeRepository.save(NoticeJpaEntity.from(notice)).toDomain();
    }
}
