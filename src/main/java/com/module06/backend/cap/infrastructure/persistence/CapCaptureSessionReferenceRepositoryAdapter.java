package com.module06.backend.cap.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 CapCaptureSessionReferenceRepository 계약을 JPA로 구현하는 어댑터.
*/
@Component
@RequiredArgsConstructor
public class CapCaptureSessionReferenceRepositoryAdapter implements CapCaptureSessionReferenceRepository {

    private final SpringDataCapCaptureSessionReferenceRepository springDataCapCaptureSessionReferenceRepository;

    @Override
    public Optional<String> findStatus(Long meetingId) {
        return springDataCapCaptureSessionReferenceRepository.findByMeetingId(meetingId)
                .map(SpringDataCapCaptureSessionReferenceRepository.SessionView::getStatus);
    }

    @Override
    public Optional<Long> findSessionId(Long meetingId) {
        return springDataCapCaptureSessionReferenceRepository.findByMeetingId(meetingId)
                .map(SpringDataCapCaptureSessionReferenceRepository.SessionView::getId);
    }
}
