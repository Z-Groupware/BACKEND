package com.module06.backend.reviewloop.judge;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * P1 컴파일 게이트 — 방금 수정된 단일 .java를 in-JVM {@code javac}로 컴파일한다.
 *
 * 왜 gradle이 아니라 javac인가: 자율 루프는 worktree 안에서 {@code ./gradlew reviewAutoFix}로 돌기 때문에,
 * 라운드마다 다시 {@code ./gradlew compileJava}를 부르면 같은 프로젝트 디렉터리 락에 걸린다(중첩 gradle).
 * 그래서 라운드 게이트는 JavaCompiler로 단일 파일만 빠르게 검증하고,
 * 교차 파괴(다른 파일을 깨는 수정)와 전체 테스트는 루프 종료 후 래퍼(review-autoloop.sh)의
 * {@code ./gradlew test} 1회가 커밋 직전에 잡는다.
 *
 * classpath는 실행 중인 reviewAutoFix JVM의 것(= main.runtimeClasspath + compileOnly[lombok]).
 * lombok이 classpath에 있으면 javac가 ServiceLoader로 어노테이션 프로세서를 자동 실행하므로
 * {@code -proc:none}을 쓰지 않는다(안 쓰면 @Getter 등 생성 코드 참조가 컴파일 실패로 오탐).
 */
public class CompileVerification implements VerificationPort {

    @Override
    public VerifyResult verify(Path filePath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // JRE로 실행되면 컴파일러가 없다 — 게이트를 막지 않고 통과(경고).
            return new VerifyResult(true, "시스템 javac 없음(JRE 실행) → 컴파일 게이트 스킵");
        }
        String classpath = System.getProperty("java.class.path", "");
        try {
            Path outDir = Files.createTempDirectory("autoloop-javac");
            StringWriter diag = new StringWriter();
            try (StandardJavaFileManager fm =
                         compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(filePath.toFile());
                List<String> options = List.of(
                        "-classpath", classpath,
                        "-d", outDir.toString(),
                        "-encoding", "UTF-8");
                boolean ok = compiler.getTask(diag, fm, null, options, null, units).call();
                return new VerifyResult(ok, diag.toString());
            }
        } catch (IOException e) {
            return new VerifyResult(false, "컴파일 게이트 IO 오류: " + e.getMessage());
        }
    }
}
