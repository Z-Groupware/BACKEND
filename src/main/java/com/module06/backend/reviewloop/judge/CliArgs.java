package com.module06.backend.reviewloop.judge;

/**
 * 리뷰 루프 CLI 진입점들의 공용 인자 파싱 — {@code --key value} / {@code --flag} 형태.
 * 러너·기록기가 각자 복사해 쓰던 것을 한 곳으로(중복 방지 = CONV_001 규칙 준수).
 */
final class CliArgs {

    /** {@code --key value} 의 value, 없으면 def. */
    static String value(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return def;
    }

    /** {@code --flag} 존재 여부. */
    static boolean flag(String[] args, String key) {
        for (String a : args) {
            if (a.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private CliArgs() {
    }
}
