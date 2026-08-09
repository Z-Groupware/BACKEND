package com.module06.backend.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* companyId/memberId 없이는 테넌트 격리가 성립하지 않으므로(모성진 요청), 필수값 검증을 확인한다. */
@DisplayName("알림 도메인 모델")
class NotificationTest {

    @Test
    @DisplayName("필수값이 있으면 정상 생성된다")
    void createsWithRequiredFields() {
        Notification notification = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");

        assertThat(notification.getCompanyId()).isEqualTo(1L);
        assertThat(notification.getMemberId()).isEqualTo(7L);
        assertThat(notification.getType()).isEqualTo("MEETING_CANCELED");
        assertThat(notification.getMeetingId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("meetingId는 없어도(null) 생성된다 — DB 컬럼 자체가 nullable이다")
    void allowsNullMeetingId() {
        Notification notification = Notification.create(1L, 7L, "MEETING_REMINDER", null, "알림 메시지");

        assertThat(notification.getMeetingId()).isNull();
    }

    @Test
    @DisplayName("companyId가 없으면 거절한다")
    void rejectsMissingCompanyId() {
        assertThatThrownBy(() -> Notification.create(null, 7L, "MEETING_CANCELED", 500L, "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("memberId가 없으면 거절한다")
    void rejectsMissingMemberId() {
        assertThatThrownBy(() -> Notification.create(1L, null, "MEETING_CANCELED", 500L, "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("type이 비어있으면 거절한다")
    void rejectsBlankType() {
        assertThatThrownBy(() -> Notification.create(1L, 7L, " ", 500L, "메시지"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("message가 비어있으면 거절한다")
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> Notification.create(1L, 7L, "MEETING_CANCELED", 500L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
