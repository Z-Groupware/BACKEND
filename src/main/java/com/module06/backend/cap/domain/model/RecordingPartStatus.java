package com.module06.backend.cap.domain.model;

/**
 * 청크(recording_part) 한 조각의 생명주기 상태. 이태연 V5.1 recording_part.status ENUM과 1:1로 맞춘다.
 * <ul>
 *   <li>{@code PRESIGNED} : presign으로 URL만 발급되고 아직 업로드 통보 전(이 API는 현재 이 상태 행을 만들지 않음).</li>
 *   <li>{@code UPLOADED}  : CAP-07 완료 통보를 받아 실제 업로드가 확인된 상태 — 이 도메인이 기록하는 상태.</li>
 *   <li>{@code ASSEMBLED} : 조립(ffmpeg)까지 끝나 블록/최종본에 반영된 상태(조립 단계 소관).</li>
 *   <li>{@code MISSING}   : 연속성 검증에서 비어있는 것으로 판정된 순번.</li>
 * </ul>
 */
public enum RecordingPartStatus {
    PRESIGNED,
    UPLOADED,
    ASSEMBLED,
    MISSING
}
