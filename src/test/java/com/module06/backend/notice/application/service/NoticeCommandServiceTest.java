package com.module06.backend.notice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.application.command.DeleteNoticeCommand;
import com.module06.backend.notice.application.command.UpdateNoticeCommand;
import com.module06.backend.notice.application.event.NoticeCreatedEvent;
import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;

/* NOTI-03~05 공지 작성·수정·삭제 서비스의 인증 정보·권한·입력·저장을 검증한다. */
@DisplayName("공지 작성·수정·삭제 서비스")
class NoticeCommandServiceTest {

    /* 수정 시각을 2026-08-09 13:40:02 KST로 고정하는 테스트 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T04:40:02Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* OWNER의 인증 정보와 본문이 신규 공지로 저장되고 생성 식별자가 반환되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 자기 회사에 공지를 작성한다")
    void createsNoticeForOwnerCompany() {
        /* 저장할 공지를 기록하고 데이터베이스 식별자 31을 반영하는 저장소 대역을 만든다. */
        Notice[] capturedNotice = new Notice[1];
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.generatedId = 31L;
        repository.savedNoticeConsumer = notice -> capturedNotice[0] = notice;
        NoticeCreatedEvent[] capturedEvent = new NoticeCreatedEvent[1];
        NoticeCommandService service = new NoticeCommandService(
                repository,
                FIXED_CLOCK,
                event -> capturedEvent[0] = event
        );

        /* 회사 10의 OWNER 3이 제목 가장자리 공백과 개행 본문을 가진 공지를 작성한다. */
        var result = service.createNotice(new CreateNoticeCommand(
                10L,
                3L,
                "OWNER",
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

        /* 저장된 식별자·회사·정규화된 제목으로 공지 등록 이벤트를 발행해야 한다. */
        assertThat(capturedEvent[0]).isEqualTo(new NoticeCreatedEvent(
                31L,
                10L,
                "회의실 예약과 참석 안내"
        ));
    }

    /* ADMIN도 OWNER와 동일하게 공지를 작성할 수 있는지 검증한다. */
    @Test
    @DisplayName("ADMIN도 공지를 작성할 수 있다")
    void allowsAdminToCreateNotice() {
        /* 저장된 공지에 식별자 32를 반영하는 단순 저장소 대역을 만든다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.generatedId = 32L;
        NoticeCommandService service = service(repository);

        /* 회사 10의 ADMIN 4가 정상 제목과 본문으로 공지를 작성한다. */
        var result = service.createNotice(new CreateNoticeCommand(
                10L,
                4L,
                "ADMIN",
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
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* 두 비관리 역할 모두 공지 관리 권한 오류로 처리돼야 한다. */
        assertErrorCode(() -> service.createNotice(command("LEADER", "제목", "본문")), "NT-002");
        assertErrorCode(() -> service.createNotice(command("MEMBER", "제목", "본문")), "NT-002");
    }

    /* Bean Validation을 우회한 잘못된 Command도 서비스에서 NT-003으로 거절하는지 검증한다. */
    @Test
    @DisplayName("잘못된 제목과 본문을 NT-003으로 거절한다")
    void rejectsInvalidNoticeInput() {
        /* 입력 검증 전에 저장소가 호출되면 실패하도록 서비스 대역을 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* null Command와 공백·길이 초과 제목 및 공백 본문을 모두 공지 입력 오류로 처리한다. */
        assertErrorCode(() -> service.createNotice(null), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", " ", "본문")), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", "가".repeat(201), "본문")), "NT-003");
        assertErrorCode(() -> service.createNotice(command("OWNER", "제목", " \n ")), "NT-003");
    }

    /* OWNER가 자기 회사의 활성 공지를 전체 수정하고 고정 수정 시각을 기록하는지 검증한다. */
    @Test
    @DisplayName("OWNER가 활성 공지의 제목과 본문을 수정한다")
    void updatesActiveNoticeForOwnerCompany() {
        /* 회사 10의 기존 공지를 반환하고 저장 대상을 기록하는 저장소 대역을 만든다. */
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 3, 10, 12);
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.noticeToFind = Optional.of(Notice.reconstitute(
                41L,
                10L,
                "기존 제목",
                "기존 본문",
                3L,
                null,
                createdAt,
                null
        ));
        NoticeCommandService service = service(repository);

        /* 회사 10의 OWNER 3이 제목 가장자리 공백과 개행 본문으로 공지 41을 수정한다. */
        var result = service.updateNotice(new UpdateNoticeCommand(
                10L,
                41L,
                3L,
                "OWNER",
                "  개정 공지  ",
                "개정 첫 줄\n개정 두 번째 줄"
        ));

        /* 수정 대상 조회는 인증 회사와 경로 식별자를 함께 사용해야 한다. */
        assertThat(repository.requestedCompanyId).isEqualTo(10L);
        assertThat(repository.requestedNoticeId).isEqualTo(41L);

        /* 제목은 정규화되고 본문·생성 이력은 보존되며 고정 KST 수정 시각이 기록돼야 한다. */
        assertThat(repository.savedNotice.getTitle()).isEqualTo("개정 공지");
        assertThat(repository.savedNotice.getContent()).isEqualTo("개정 첫 줄\n개정 두 번째 줄");
        assertThat(repository.savedNotice.getCreatedAt()).isEqualTo(createdAt);
        assertThat(repository.savedNotice.getUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 9, 13, 40, 2));

        /* 수정 응답은 상세 화면에 필요한 최종 공지 전체를 반환해야 한다. */
        assertThat(result.noticeId()).isEqualTo(41L);
        assertThat(result.title()).isEqualTo("개정 공지");
        assertThat(result.content()).isEqualTo("개정 첫 줄\n개정 두 번째 줄");
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 9, 13, 40, 2));
    }

    /* ADMIN도 OWNER와 같은 공지 수정 권한을 갖는지 검증한다. */
    @Test
    @DisplayName("ADMIN도 공지를 수정할 수 있다")
    void allowsAdminToUpdateNotice() {
        /* 회사 10의 활성 공지를 반환하는 저장소와 공지 명령 서비스를 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.noticeToFind = Optional.of(activeNotice());
        NoticeCommandService service = service(repository);

        /* ADMIN 역할로 공지 수정 유스케이스를 호출한다. */
        var result = service.updateNotice(updateCommand("ADMIN", "관리자 개정", "관리자 개정 본문"));

        /* ADMIN 요청도 정상 저장돼 최종 제목을 반환해야 한다. */
        assertThat(result.title()).isEqualTo("관리자 개정");
    }

    /* 비관리 역할이 서비스 직접 호출로 수정 권한을 우회하지 못하는지 검증한다. */
    @Test
    @DisplayName("LEADER와 MEMBER의 공지 수정을 NT-002로 거절한다")
    void rejectsNonManagerRolesForUpdate() {
        /* 권한 검증 뒤 저장소에 접근하면 실패하도록 저장소 대역을 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* 두 비관리 역할 모두 공지 관리 권한 오류로 처리돼야 한다. */
        assertErrorCode(() -> service.updateNotice(updateCommand("LEADER", "제목", "본문")), "NT-002");
        assertErrorCode(() -> service.updateNotice(updateCommand("MEMBER", "제목", "본문")), "NT-002");
    }

    /* 타 회사·삭제·없는 공지에 해당하는 빈 저장소 결과를 NT-001로 숨기는지 검증한다. */
    @Test
    @DisplayName("수정할 활성 공지가 없으면 NT-001을 반환한다")
    void rejectsMissingInactiveOrOtherCompanyNotice() {
        /* 수정 대상 조회가 빈 결과를 반환하는 저장소로 서비스를 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        NoticeCommandService service = service(repository);

        /* 존재 이유를 구분하지 않는 공지 없음 오류로 처리해야 한다. */
        assertErrorCode(() -> service.updateNotice(updateCommand("OWNER", "제목", "본문")), "NT-001");
        assertThat(repository.savedNotice).isNull();
    }

    /* 웹 검증을 우회한 잘못된 수정 Command도 NT-003으로 거절하는지 검증한다. */
    @Test
    @DisplayName("잘못된 공지 수정 입력을 NT-003으로 거절한다")
    void rejectsInvalidUpdateInput() {
        /* 입력 오류가 저장소에 도달하면 실패하도록 저장소 대역을 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* null Command와 잘못된 경로 식별자·제목·본문을 모두 NT-003으로 처리한다. */
        assertErrorCode(() -> service.updateNotice(null), "NT-003");
        assertErrorCode(() -> service.updateNotice(new UpdateNoticeCommand(
                10L, 0L, 3L, "OWNER", "제목", "본문"
        )), "NT-003");
        assertErrorCode(() -> service.updateNotice(updateCommand("OWNER", " ", "본문")), "NT-003");
        assertErrorCode(() -> service.updateNotice(updateCommand("OWNER", "가".repeat(201), "본문")), "NT-003");
        assertErrorCode(() -> service.updateNotice(updateCommand("OWNER", "제목", " \n ")), "NT-003");
    }

    /* OWNER가 자기 회사의 활성 공지를 소프트 삭제하고 고정 삭제 시각을 기록하는지 검증한다. */
    @Test
    @DisplayName("OWNER가 활성 공지를 소프트 삭제한다")
    void softDeletesActiveNoticeForOwnerCompany() {
        /* 회사 10의 활성 공지를 반환하는 저장소와 삭제 서비스를 구성한다. */
        Notice current = activeNotice();
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.noticeToFind = Optional.of(current);
        NoticeCommandService service = service(repository);

        /* 회사 10의 OWNER 3이 공지 41을 삭제한다. */
        service.deleteNotice(deleteCommand("OWNER"));

        /* 삭제 대상 조회는 인증 회사와 경로 식별자를 함께 사용해야 한다. */
        assertThat(repository.requestedCompanyId).isEqualTo(10L);
        assertThat(repository.requestedNoticeId).isEqualTo(41L);

        /* 기존 원본과 수정 이력은 유지하고 고정 KST 삭제 시각만 기록돼야 한다. */
        assertThat(repository.savedNotice.getId()).isEqualTo(current.getId());
        assertThat(repository.savedNotice.getTitle()).isEqualTo(current.getTitle());
        assertThat(repository.savedNotice.getContent()).isEqualTo(current.getContent());
        assertThat(repository.savedNotice.getCreatedAt()).isEqualTo(current.getCreatedAt());
        assertThat(repository.savedNotice.getUpdatedAt()).isEqualTo(current.getUpdatedAt());
        assertThat(repository.savedNotice.getDeletedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 9, 13, 40, 2));
    }

    /* ADMIN도 OWNER와 동일한 소프트 삭제 권한을 갖는지 검증한다. */
    @Test
    @DisplayName("ADMIN도 공지를 삭제할 수 있다")
    void allowsAdminToDeleteNotice() {
        /* 회사 10의 활성 공지를 반환하는 저장소와 공지 명령 서비스를 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.noticeToFind = Optional.of(activeNotice());
        NoticeCommandService service = service(repository);

        /* ADMIN 역할로 공지 삭제 유스케이스를 호출한다. */
        service.deleteNotice(deleteCommand("ADMIN"));

        /* ADMIN 요청도 정상 저장돼 deletedAt이 기록되어야 한다. */
        assertThat(repository.savedNotice.getDeletedAt()).isNotNull();
    }

    /* 비관리 역할이 서비스 직접 호출로 삭제 권한을 우회하지 못하는지 검증한다. */
    @Test
    @DisplayName("LEADER와 MEMBER의 공지 삭제를 NT-002로 거절한다")
    void rejectsNonManagerRolesForDelete() {
        /* 권한 검증 뒤 저장소에 접근하면 실패하도록 저장소 대역을 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* 두 비관리 역할 모두 공지 관리 권한 오류로 처리돼야 한다. */
        assertErrorCode(() -> service.deleteNotice(deleteCommand("LEADER")), "NT-002");
        assertErrorCode(() -> service.deleteNotice(deleteCommand("MEMBER")), "NT-002");
    }

    /* 타 회사·이미 삭제·없는 공지의 빈 저장소 결과를 NT-001로 숨기는지 검증한다. */
    @Test
    @DisplayName("삭제할 활성 공지가 없으면 NT-001을 반환한다")
    void rejectsMissingDeletedOrOtherCompanyNoticeForDelete() {
        /* 삭제 대상 조회가 빈 결과를 반환하는 저장소로 서비스를 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        NoticeCommandService service = service(repository);

        /* 존재하지 않는 이유를 구분하지 않는 공지 없음 오류로 처리해야 한다. */
        assertErrorCode(() -> service.deleteNotice(deleteCommand("OWNER")), "NT-001");
        assertThat(repository.savedNotice).isNull();
    }

    /* 잘못된 삭제 Command가 저장소 접근 전에 NT-003으로 거절되는지 검증한다. */
    @Test
    @DisplayName("잘못된 공지 삭제 입력을 NT-003으로 거절한다")
    void rejectsInvalidDeleteInput() {
        /* 입력 오류가 저장소에 도달하면 실패하도록 저장소 대역을 구성한다. */
        StubNoticeCommandRepository repository = new StubNoticeCommandRepository();
        repository.failOnAccess = true;
        NoticeCommandService service = service(repository);

        /* null Command와 유효하지 않은 회사·공지·요청자 식별자를 모두 NT-003으로 처리한다. */
        assertErrorCode(() -> service.deleteNotice(null), "NT-003");
        assertErrorCode(() -> service.deleteNotice(new DeleteNoticeCommand(
                0L, 41L, 3L, "OWNER"
        )), "NT-003");
        assertErrorCode(() -> service.deleteNotice(new DeleteNoticeCommand(
                10L, 0L, 3L, "OWNER"
        )), "NT-003");
        assertErrorCode(() -> service.deleteNotice(new DeleteNoticeCommand(
                10L, 41L, 0L, "OWNER"
        )), "NT-003");
    }

    /* 역할과 제목·본문만 바꿀 수 있는 정상 인증 작성 Command를 만든다. */
    private CreateNoticeCommand command(String role, String title, String content) {
        /* 회사 10의 구성원 3을 공통 인증 값으로 사용한다. */
        return new CreateNoticeCommand(10L, 3L, role, title, content);
    }

    /* 역할과 제목·본문만 바꿀 수 있는 정상 인증 수정 Command를 만든다. */
    private UpdateNoticeCommand updateCommand(String role, String title, String content) {
        /* 회사 10의 구성원 3이 공지 41을 수정하는 공통 인증 값을 사용한다. */
        return new UpdateNoticeCommand(10L, 41L, 3L, role, title, content);
    }

    /* 역할만 바꿀 수 있는 정상 인증 공지 삭제 Command를 만든다. */
    private DeleteNoticeCommand deleteCommand(String role) {
        /* 회사 10의 구성원 3이 공지 41을 삭제하는 공통 인증 값을 사용한다. */
        return new DeleteNoticeCommand(10L, 41L, 3L, role);
    }

    /* 이벤트 발행 자체를 검증하지 않는 테스트에서 사용하는 no-op 발행기와 서비스를 조립한다. */
    private NoticeCommandService service(NoticeCommandRepository repository) {
        /* 공지 수정·삭제 및 오류 검증은 등록 이벤트를 소비하지 않도록 빈 발행기를 주입한다. */
        return new NoticeCommandService(repository, FIXED_CLOCK, event -> { });
    }

    /* 수정 서비스 테스트에서 공통으로 사용하는 회사 10의 활성 공지를 만든다. */
    private Notice activeNotice() {
        /* 생성 이력을 가진 수정 전 공지 41을 반환한다. */
        return Notice.reconstitute(
                41L,
                10L,
                "기존 제목",
                "기존 본문",
                3L,
                null,
                LocalDateTime.of(2026, 8, 3, 10, 12),
                null
        );
    }

    /* 실행 결과가 기대한 공지 BusinessException 코드인지 검증한다. */
    private void assertErrorCode(Runnable executable, String expectedCode) {
        /* 서비스가 다른 예외로 입력·권한 실패를 숨기지 않는지 확인한다. */
        assertThatThrownBy(executable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 공지 명령 서비스의 조회·저장 호출과 결과를 제어하는 테스트 저장소다. */
    private static final class StubNoticeCommandRepository implements NoticeCommandRepository {

        /* 수정 대상 조회 결과이며 기본값은 공지가 없는 상태다. */
        private Optional<Notice> noticeToFind = Optional.empty();

        /* 신규 공지 저장 시 데이터베이스가 부여할 식별자다. */
        private Long generatedId = 1L;

        /* 테스트가 저장 직전 공지 상태를 별도로 기록할 선택 콜백이다. */
        private java.util.function.Consumer<Notice> savedNoticeConsumer = notice -> { };

        /* 저장소 접근 자체가 허용되지 않는 테스트에서 즉시 실패할지 나타낸다. */
        private boolean failOnAccess;

        /* 수정 대상 조회에 전달된 인증 회사 식별자다. */
        private Long requestedCompanyId;

        /* 수정 대상 조회에 전달된 공지 식별자다. */
        private Long requestedNoticeId;

        /* 마지막으로 저장 요청된 공지 상태다. */
        private Notice savedNotice;

        /* 회사와 활성 범위가 적용된 수정 대상 조회를 흉내 낸다. */
        @Override
        public Optional<Notice> findActiveNotice(Long companyId, Long noticeId) {
            /* 권한·입력 오류 뒤 저장소 접근이 발생하면 테스트를 실패시킨다. */
            rejectUnexpectedAccess();
            this.requestedCompanyId = companyId;
            this.requestedNoticeId = noticeId;
            return noticeToFind;
        }

        /* 신규·수정 공지를 기록하고 신규 공지에만 생성 식별자를 반영한다. */
        @Override
        public Notice save(Notice notice) {
            /* 권한·입력 오류 뒤 저장소 접근이 발생하면 테스트를 실패시킨다. */
            rejectUnexpectedAccess();
            this.savedNotice = notice;
            savedNoticeConsumer.accept(notice);

            /* 수정 공지는 기존 식별자·시각을 그대로 반환하고 신규 공지만 식별자를 생성한다. */
            if (notice.getId() != null) {
                return notice;
            }
            return Notice.reconstitute(
                    generatedId,
                    notice.getCompanyId(),
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getCreatedBy(),
                    notice.getDeletedAt(),
                    notice.getCreatedAt(),
                    notice.getUpdatedAt()
            );
        }

        /* 저장소 접근 금지 테스트에서 호출 경로를 명확하게 실패시킨다. */
        private void rejectUnexpectedAccess() {
            /* 입력·권한 검증보다 저장소 접근이 먼저 일어나면 AssertionError를 발생시킨다. */
            if (failOnAccess) {
                throw new AssertionError("검증 실패 요청에서 저장소에 접근하면 안 됩니다.");
            }
        }
    }
}
