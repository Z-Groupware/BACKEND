package com.module06.backend.reviewloop.judge;

import java.nio.file.Path;

/**
 * P1 안전막 seam — 수정된 파일이 실제로 컴파일되는지 검증한다.
 * 테스트=stub, 런타임=CompileVerification(in-JVM javac). AutoLoopRunner가 주입한다.
 */
@FunctionalInterface
public interface VerificationPort {
    VerifyResult verify(Path filePath);
}
