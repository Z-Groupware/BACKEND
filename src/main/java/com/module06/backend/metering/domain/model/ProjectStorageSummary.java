package com.module06.backend.metering.domain.model;

import java.time.LocalDateTime;

/**
 * 저장소 관리 화면(/manage/storage)의 프로젝트 한 줄 — 음성 저장량 집계 결과.
 *
 * meetingCount는 "녹음이 남은 회의 수"다(회의 자체의 수가 아니다) — meeting_storage_usage에서
 * usedBytes가 0보다 큰 행만 센다. 녹음을 지우면(DeleteRecordingService) usedBytes만 0으로
 * report되고 row 자체는 남으므로, 0 초과 필터가 곧 "지금 녹음이 있는 회의"의 근사치다.
 *
 * lastRecordedAt도 같은 필터(usedBytes > 0)에서 updatedAt의 최댓값이다 — 삭제된 회의의 report
 * 시각까지 섞이면 "가장 최근에 녹음한 날짜"라는 의미가 깨진다.
 */
public record ProjectStorageSummary(Long projectId, long usedBytes, long meetingCount,
                                     LocalDateTime lastRecordedAt) {
}
