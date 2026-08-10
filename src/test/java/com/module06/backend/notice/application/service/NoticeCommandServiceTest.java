package com.module06.backend.notice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;

/* NOTI-03 공지 작성 서비스의 인증 정보·권한·입력·저장 결과를 검증한다. */
@DisplayName("NOTI-03 공지 작성 서비스")
class NoticeCommandServiceTest {

    /* OWNER의 인증 정보와 본문이 신규 공지로 저장되고 생성 식별자가 반환되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 자기 회사에 공지를 작성한다")
    void createsNoticeForOwnerCompany() {
        /* 저장할 공지를 기록하고 데이터베이스 식별자 31을 반영하는 저장소 대역을 만든다. */
        Notice[] capturedNotice = new Notice[1];
        NoticeCommandRepository repository = notice -> {
            capturedNotice[0] = notice;
            return Notice.reconstitute(
                    31L,
                    notice.getCompanyId(),
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getCreatedBy(),
                    null,
                    null,
                    null
            );
        };
        NoticeCommandService service = new NoticeCommandService(repository);

        /* 회사 10의 OWNER 3이 제목 가장자리 공백과 개행 본문을 가진 공지를 작성한다. */
        var result = service.createNotice(new CreateNoticeCommand(
                10L,
                3L,
                "OWNER",
                false,
                "  회의실 예약과 참석 안내  ",
                "첫 번째 줄\n두 번째 줄"
        ));

        /* 인증 회사·작성자와 정규화된 제목이 저장되고 본문의 개행 원문은 유지돼야 한다. */
        assertThat(capturedNotice[0].getCompanyId()).isEqualTo(10L);
        assertThat(capturedNotice[0].getCreatedBy()).isEqualTo(3L);
        assertThat(capturedNotice[0].getTitle()).isEqualTo("회의실 예약과 참석 안내");
        assertThat(capturedNotice[0].getContent()).isEqualTo("첫 번째 줄\n두 번째 줄");

        /* 저장소에서 생성된 공지 식별자만 작성 결과로 반환돼야 한다. */
        assertThat(result.noticeId()).isEqualTo(31L);
    }

    /* ADMIN도 OWNER와 동일하게 공지를 작성할 수 있는지 검증한다. */
    @Test
    @DisplayName("ADMIN도 공지를 작성할 수 있다")
    void allowsAdminToCreateNotice() {
        /* 저장된 공지에 식별자 32를 반영하는 단순 저장소 대역을 만든다. */
        NoticeCommandService service = new NoticeCommandService(notice -> Notice.reconstitute(
                32L,
                notice.getCompanyId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedBy(),
                null,
                null,
                null
        ));

        /* 회사 10의 MEMBER이면서 관리자 겸직자인 구성원 4가 정상 제목과 본문으로 공지를 작성한다. */
        var result = service.createNotice(new CreateNoticeCommand(
                10L,
                4L,
                "MEMBER",
                true,
                "관리자 공지",
                "관리자 공지 본문"
        ));

        /* ADMIN 요청도 정상 저장돼 생성 식별자를 받아야 한다. */
        assertThat(result.noticeId()).isEqualTo(32L);
    }

    /* LEADER와 MEMBER가 내부 호출로 작성 서비스를 우회하지 못하는지 검증한다. */
    @Test
    @DisplayName("LEADER와 MEMBER의 공지 작성을 NT-002로 거절한다")
    void rejectsNonManagerRoles() {
        /* 권한 거절 전에 저장소가 호출되면 실패하도록 서비스 대역을 구성한다. */
        NoticeCommandService service = new NoticeCommandService(notice -> {
            throw new AssertionError("권한 없는 공지를 저장하면 안 됩니다.");
        });

        /* 두 비관리 역할 모두 공지 관리 권한 오류로 처리돼야 한다. */
        assertErrorCode(() -> service.createNotice(command("LEADER", false, "제목", "본문")), "NT-002");
        assertErrorCode(() -> service.createNotice(command("MEMBER", false, "제목", "본문")), "NT-002");
    }

    /* Bean Validation을 우회한 잘못된 Command도 서비스에서 NT-003으로 거절하는지 검증한다. */
    @Test
    @DisplayName("잘못된 제목과 본문을 NT-003으로 거절한다")
    void rejectsInvalidNoticeInput() {
        /* 입력 검증 전에 저장소가 호출되면 실패하도록 서비스 대역을 구성한다. */
        NoticeCommandService service = new NoticeCommandService(notice -> {
            throw new AssertionError("잘못된 공지를 저장하면 안 됩니다.");
        });

        /* null Command와 공백·길이 초과 제목 및 공백 본문을 모두 공지 입력 오류로 처리한다. */
        assertErrorCode(() -> service.createNotice(null), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", false, " ", "본문")), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", false, "가".repeat(201), "본문")), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", false, "제목", " \n ")), "NT-003");
    }

    /* 역할·관리자 여부와 제목·본문만 바꿀 수 있는 정상 인증 작성 Command를 만든다. */
    private CreateNoticeCommand command(String role, boolean isAdmin, String title, String content) {
        /* 회사 10의 구성원 3을 공통 인증 값으로 사용한다. */
        return new CreateNoticeCommand(10L, 3L, role, isAdmin, title, content);
    }

    /* 실행 결과가 기대한 공지 BusinessException 코드인지 검증한다. */
    private void assertErrorCode(Runnable executable, String expectedCode) {
        /* 서비스가 다른 예외로 입력·권한 실패를 숨기지 않는지 확인한다. */
        assertThatThrownBy(executable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
