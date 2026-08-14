package com.module06.backend.meeting.presentation.api.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import com.module06.backend.meeting.application.command.CreateOnlineMeetingCommand;

/*
 * MEET-18 비대면 회의 개설 요청 본문이다.
 *
 * 회의실과 시작·종료 일시는 물리 회의 전용 값이라 애초에 받지 않는다.
 * 회사와 개설자 정보는 조작 가능한 본문에서 받지 않고 인증 principal에서 주입한다.
 */
public record CreateOnlineMeetingRequest(
        /* 회의 목록과 상세 화면에 표시할 제목이다. */
        @NotBlank @Size(max = 200) String title,

        /* 회의가 소속될 필수 프로젝트 식별자다. */
        @NotNull @Positive Long projectId,

        /* 액션 보드에서 회의를 예약한 경우 연결할 선택 식별자다. */
        @Positive Long relatedActionId,

        /* 개설자를 제외하고 선택한 참석자 식별자 목록이며 최소 한 명이 필요하다. */
        @NotNull @Size(min = 1) List<@NotNull @Positive Long> attendeeMemberIds,

        /* 회의에서 다룰 필수 대주제이며 meeting_topic의 MAIN으로 저장한다. */
        @NotBlank @Size(max = 300) String mainTopic,

        /* 대주제 아래에 표시할 필수 소주제 목록이며 하나 이상이어야 한다. */
        @NotNull @Size(min = 1) List<@NotBlank @Size(max = 300) String> subTopics,

        /* 프론트가 Presigned URL로 S3 직접 업로드를 마친 녹음 파일 참조다. */
        @NotNull @Valid RecordingRequest recording
) {

    /* 실제 파일 바이트가 아닌 S3 객체와 검증용 메타데이터만 받는다. */
    public record RecordingRequest(
            @NotBlank String s3Key,
            @NotBlank String fileName,
            @NotBlank String contentType,
            @NotNull @Positive Long sizeBytes
    ) {
        private CreateOnlineMeetingCommand.RecordingReference toCommand() {
            return new CreateOnlineMeetingCommand.RecordingReference(
                    s3Key, fileName, contentType, sizeBytes);
        }
    }

    /* 외부에서 전달된 목록을 요청 객체 생성 시점에 불변 복사한다. */
    public CreateOnlineMeetingRequest {
        /* null은 Bean Validation이 처리하고 유효한 목록만 방어적으로 복사한다. */
        if (attendeeMemberIds != null) {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }

        /* null은 Bean Validation이 처리하고 유효한 소주제 목록만 방어적으로 복사한다. */
        if (subTopics != null) {
            subTopics = List.copyOf(subTopics);
        }
    }

    /* 인증 정보와 본문을 합쳐 애플리케이션 유스케이스 명령으로 변환한다. */
    public CreateOnlineMeetingCommand toCommand(Long companyId, Long hostMemberId, Long hostTeamId, String hostRole) {
        /* 테넌트와 개설자 식별자는 인증 principal에서만 가져온다. */
        return new CreateOnlineMeetingCommand(
                companyId,
                hostMemberId,
                hostTeamId,
                hostRole,
                title,
                projectId,
                /* 비대면 회의는 "녹음 파일 제출 → AI 요약"이 전제라 동의 없이는 성립하지 않아 서버가 항상 true로 고정한다. */
                true,
                relatedActionId,
                attendeeMemberIds,
                mainTopic,
                subTopics,
                recording == null ? null : recording.toCommand()
        );
    }
}
