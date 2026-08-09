package com.module06.backend.notice.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;
import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;

/* NOTI-03·04 공지 저장과 수정 대상 조회 계약을 Spring Data JPA로 구현하는 명령 어댑터다. */
@Component
@RequiredArgsConstructor
public class NoticeCommandPersistenceAdapter implements NoticeCommandRepository {

    /* notice 테이블의 활성 공지를 조회하고 신규·수정 행을 저장하는 기술 저장소다. */
    private final SpringDataNoticeRepository springDataNoticeRepository;

    /* 회사·식별자·활성 조건을 한 쿼리에 적용해 수정할 수 있는 공지만 반환한다. */
    @Override
    public Optional<Notice> findActiveNotice(Long companyId, Long noticeId) {
        /* 타 회사·삭제·없는 공지는 모두 빈 결과가 되어 상위 계층에서 동일한 NT-001로 처리된다. */
        return springDataNoticeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(noticeId, companyId)
                .map(NoticeJpaEntity::toDomain);
    }

    /* 신규 또는 수정 공지를 JPA 엔티티로 저장하고 생명주기 값이 반영된 도메인 모델을 반환한다. */
    @Override
    public Notice save(Notice notice) {
        /* 도메인과 영속성 매핑을 엔티티 변환 메서드에 위임하고 저장 결과를 다시 도메인으로 복원한다. */
        return springDataNoticeRepository.save(NoticeJpaEntity.from(notice)).toDomain();
    }
}
