package com.module06.backend.capture.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/*
 * 받아쓰기 진행도와 **남은 시간 추정**(CAP-06 의 blocks · estimatedRemainingSec).
 *
 * <h2>이 회의의 실측으로만 잰다</h2>
 * 상수(예: "오디오 1분당 6초")를 박지 않는다. Transcribe 의 처리 속도는 계정 동시 실행 수·
 * 리전 부하·오디오 길이에 따라 몇 배로 흔들리고, 상수로 두면 화면이 매번 틀린 숫자를 자신 있게
 * 보여준다. **이 회의에서 실제로 끝난 블록이 얼마나 걸렸는지**를 재서 남은 오디오에 곱한다.
 *
 * <h2>못 재면 null 이다 — 지어내지 않는다</h2>
 * 끝난 블록이 없으면 곱할 비율이 없다. 그때 0 을 주면 "곧 끝난다"로 읽히고, 큰 수를 주면
 * 근거 없는 겁주기다. 명세의 이 필드가 오래 비어 있던 이유가 그것이고(「지어낼 방법이 없다」),
 * 지금 채우는 것은 **잴 수 있게 됐기 때문**이지 규칙이 느슨해진 것이 아니다.
 *
 * <h2>분석 단계는 재지 않는다</h2>
 * 받아쓰기가 끝난 뒤의 계층 소요는 여기서 답하지 않는다(null). 계층은 주제 수·tuple 수만큼
 * 반복 호출되는데 그 수는 L2·L4 가 돌기 전에는 모른다 — 앞 계층 소요로 뒤를 재면 자릿수가
 * 틀린다. 그건 계층별 실행 시간 이력이 쌓인 뒤에 별도로 정할 일이다.
 */
public record SttProgress(int total, int done, int failed, Integer estimatedRemainingSec) {

    /*
     * 블록 목록에서 진행도를 만든다.
     *
     * @param blocks 이 회의의 블록 전부. 상태와 구간, 그리고 끝난 것의 실측 시각이 필요하다
     * @param now    남은 시간 추정의 기준 시각
     */
    public static SttProgress of(List<BlockTiming> blocks, LocalDateTime now) {
        if (blocks == null || blocks.isEmpty()) {
            /*
             * 받아쓰기를 한 적이 없는 회의다. total=0 이고 남은 시간은 null 이다 —
             * 0 초를 주면 "받아쓰기가 끝났다"로 읽힌다.
             */
            return new SttProgress(0, 0, 0, null);
        }

        int done = 0;
        int failed = 0;
        long measuredWallMs = 0;
        long measuredAudioMs = 0;
        long remainingAudioMs = 0;

        for (BlockTiming block : blocks) {
            switch (block.status()) {
                case DONE -> {
                    done++;
                    /*
                     * 실측 재료는 **DONE 블록만** 쓴다. FAILED 는 중간에 끊긴 시간이라 처리
                     * 속도가 아니고, 그걸 섞으면 비율이 실제보다 짧거나 길게 왜곡된다.
                     */
                    if (block.startedAt() != null && block.finishedAt() != null) {
                        long wall = Duration.between(block.startedAt(), block.finishedAt()).toMillis();
                        if (wall > 0) {
                            measuredWallMs += wall;
                            measuredAudioMs += block.audioMs();
                        }
                    }
                }
                case FAILED -> failed++;
                // PENDING·QUEUED·RUNNING — 아직 결과가 없다. 남은 오디오에 든다.
                default -> remainingAudioMs += remainingAudioOf(block, now);
            }
        }

        return new SttProgress(blocks.size(), done, failed,
                estimate(remainingAudioMs, measuredAudioMs, measuredWallMs));
    }

    /*
     * 남은 오디오. RUNNING 블록은 **이미 진행한 만큼을 뺀다.**
     *
     * 빼지 않으면 10분 블록이 9분째 돌고 있어도 10분치가 남은 것으로 세어져, 화면의 남은 시간이
     * 끝날 때까지 줄지 않는다 — 진행 표시가 멈춰 있으면 사람은 고장으로 읽는다.
     *
     * 실측 비율이 예상보다 느려 이미 지난 시간이 오디오 길이를 넘으면 0 으로 접는다. 음수를
     * 남기면 다른 블록의 남은 시간을 상계해 전체 추정이 실제보다 짧아진다.
     */
    private static long remainingAudioOf(BlockTiming block, LocalDateTime now) {
        if (block.startedAt() == null) {
            return block.audioMs();
        }
        long elapsed = Duration.between(block.startedAt(), now).toMillis();
        return Math.max(0, block.audioMs() - Math.max(0, elapsed));
    }

    /*
     * @return 남은 초. 잴 근거가 없으면 null
     */
    private static Integer estimate(long remainingAudioMs, long measuredAudioMs, long measuredWallMs) {
        if (remainingAudioMs <= 0) {
            // 남은 블록이 없다. 이건 측정 문제가 아니라 사실이므로 0 이다.
            return 0;
        }
        if (measuredAudioMs <= 0 || measuredWallMs <= 0) {
            // 이 회의에서 끝난 블록이 없어 곱할 비율이 없다.
            return null;
        }
        double secondsPerAudioMs = (double) measuredWallMs / measuredAudioMs / 1000d;
        return (int) Math.ceil(remainingAudioMs * secondsPerAudioMs);
    }

    /*
     * 추정에 필요한 블록 하나.
     *
     * @param audioMs   이 블록이 담은 오디오 길이(끝 − 시작). 남은 시간은 회의 시계가 아니라
     *                  **처리할 오디오 양**에 비례한다
     * @param startedAt 제공자가 잡은 시각. 제출 시각이 아니다(SttBlockJpaEntity#markRunning)
     */
    public record BlockTiming(
            SttBlockStatus status,
            int audioMs,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
