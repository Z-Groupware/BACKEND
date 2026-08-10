package com.module06.backend.notice.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.application.command.DeleteNoticeCommand;
import com.module06.backend.notice.application.command.UpdateNoticeCommand;
import com.module06.backend.notice.application.result.NoticeCreationResult;
import com.module06.backend.notice.application.result.NoticeUpdateResult;
import com.module06.backend.notice.application.usecase.CreateNoticeUseCase;
import com.module06.backend.notice.application.usecase.DeleteNoticeUseCase;
import com.module06.backend.notice.application.usecase.UpdateNoticeUseCase;
import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;
import com.module06.backend.notice.exception.NoticeErrorCode;

/* NOTI-03~05 공지 작성·수정·삭제의 입력·권한·저장을 조율하는 애플리케이션 서비스다. */
@Service
@RequiredArgsConstructor
public class NoticeCommandService implements CreateNoticeUseCase, UpdateNoticeUseCase, DeleteNoticeUseCase {

    /* 공지 도메인 모델을 notice 테이블에 저장하는 명령 저장소다. */
    private final NoticeCommandRepository noticeCommandRepository;

    /* 공지 수정 시각을 운영 KST와 테스트에서 같은 방식으로 결정하는 시계다. */
    private final Clock clock;

    /* 인증 회사에 OWNER·ADMIN 작성자의 공지를 저장하고 생성 식별자를 반환한다. */
    @Override
    @Transactional
    public NoticeCreationResult createNotice(CreateNoticeCommand command) {
        /* Controller를 거치지 않는 내부 호출에서도 인증 식별자와 제목·본문 계약을 검증한다. */
        validateRequiredValues(command);

        /* 애노테이션 인가를 우회한 내부 호출도 OWNER·ADMIN이 아니면 공지 작성을 거절한다. */
        if (!isNoticeManager(command.requesterRole())) {
            throw new BusinessException(NoticeErrorCode.NOTICE_MANAGEMENT_FORBIDDEN);
        }

        /* 제목 가장자리 공백만 제거하고 본문의 개행과 원문은 그대로 보존한다. */
        Notice notice = Notice.create(
                command.companyId(),
                command.requesterMemberId(),
                command.title().trim(),
                command.content()
        );

        /* 데이터베이스 생성 식별자가 반영된 공지를 저장 결과로 받는다. */
        Notice savedNotice = noticeCommandRepository.save(notice);

        /* 전체 공지 대신 작성 직후 필요한 생성 식별자만 외부 결과로 반환한다. */
        return new NoticeCreationResult(savedNotice.getId());
    }

    /* 인증 회사의 활성 공지를 전체 치환하고 수정된 공지 전체를 반환한다. */
    @Override
    @Transactional
    public NoticeUpdateResult updateNotice(UpdateNoticeCommand command) {
        /* 웹 계층을 거치지 않는 내부 호출에도 식별자·권한·본문 계약을 동일하게 적용한다. */
        validateUpdateCommand(command);

        /* 애노테이션 인가 우회 호출도 OWNER·ADMIN이 아니면 공지 수정을 거절한다. */
        if (!isNoticeManager(command.requesterRole())) {
            throw new BusinessException(NoticeErrorCode.NOTICE_MANAGEMENT_FORBIDDEN);
        }

        /* 타 회사·삭제·없는 공지를 구분하지 않고 동일한 NT-001로 숨긴다. */
        Notice currentNotice = noticeCommandRepository
                .findActiveNotice(command.companyId(), command.noticeId())
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));

        /* 제목은 정규화하고 본문 원문과 현재 KST 수정 시각을 새로운 도메인 상태에 반영한다. */
        Notice updatedNotice = currentNotice.update(
                command.title().trim(),
                command.content(),
                LocalDateTime.now(clock)
        );

        /* 수정 상태를 저장하고 데이터베이스가 반환한 최종 값을 응답 결과로 변환한다. */
        Notice savedNotice = noticeCommandRepository.save(updatedNotice);
        return NoticeUpdateResult.from(savedNotice);
    }

    /* 인증 회사의 활성 공지를 소프트 삭제해 조회 화면에서 제외한다. */
    @Override
    @Transactional
    public void deleteNotice(DeleteNoticeCommand command) {
        /* 내부 호출도 인증 식별자와 삭제 경로 계약을 준수하도록 저장소 접근 전에 검증한다. */
        validateDeleteCommand(command);

        /* 애노테이션 인가 우회 호출도 OWNER·ADMIN이 아니면 공지 삭제를 거절한다. */
        if (!isNoticeManager(command.requesterRole())) {
            throw new BusinessException(NoticeErrorCode.NOTICE_MANAGEMENT_FORBIDDEN);
        }

        /* 타 회사·이미 삭제·없는 공지를 구분하지 않고 동일한 NT-001로 숨긴다. */
        Notice currentNotice = noticeCommandRepository
                .findActiveNotice(command.companyId(), command.noticeId())
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));

        /* 현재 KST 시각을 deletedAt에 기록하고 기존 공지 이력은 그대로 유지한다. */
        Notice deletedNotice = currentNotice.softDelete(LocalDateTime.now(clock));

        /* 소프트 삭제 상태를 같은 공지 행에 저장해 이후 활성 조회에서 제외한다. */
        noticeCommandRepository.save(deletedNotice);
    }

    /* 회사·작성자·역할과 제목·본문이 NOTI-03 계약에 맞는지 확인한다. */
    private void validateRequiredValues(CreateNoticeCommand command) {
        /* 인증 식별자·역할과 공지 필드의 누락 또는 공백은 NT-003 입력 오류로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()
                || command.title() == null
                || command.title().isBlank()
                || command.content() == null
                || command.content().isBlank()
                || command.title().length() > 200) {
            throw new BusinessException(NoticeErrorCode.INVALID_NOTICE_INPUT);
        }
    }

    /* 회사·요청자·공지 식별자와 전체 치환 본문이 NOTI-04 계약에 맞는지 확인한다. */
    private void validateUpdateCommand(UpdateNoticeCommand command) {
        /* 경로·인증·본문 값이 누락되거나 유효 범위를 벗어나면 NT-003으로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.noticeId() == null
                || command.noticeId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()
                || command.title() == null
                || command.title().isBlank()
                || command.title().length() > 200
                || command.content() == null
                || command.content().isBlank()) {
            throw new BusinessException(NoticeErrorCode.INVALID_NOTICE_INPUT);
        }
    }

    /* 회사·요청자·공지 식별자가 NOTI-05 삭제 계약에 맞는지 확인한다. */
    private void validateDeleteCommand(DeleteNoticeCommand command) {
        /* 경로와 인증 원본이 누락되거나 유효 범위를 벗어나면 NT-003으로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.noticeId() == null
                || command.noticeId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()) {
            throw new BusinessException(NoticeErrorCode.INVALID_NOTICE_INPUT);
        }
    }

    /* 인증 역할이 공지 작성 권한을 가진 OWNER 또는 ADMIN인지 판단한다. */
    private boolean isNoticeManager(String role) {
        /* 명세에 확정된 두 역할만 쓰기 권한으로 인정한다. */
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }
}
