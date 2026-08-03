#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 드라이버 검증 게이트 (통합 설계 P0-b · review-loop/UNIFIED_DESIGN.md)
#
# 드라이버(Claude Code)가 Edit로 수정한 직후 자기 수정을 "결정론으로" 검증한다.
# 판정(Gemini)·수정(Claude Code)과 달리 여기엔 LLM이 없다 — 컴파일러와 테스트만 판단한다.
#
#   기본        : 전체 컴파일 (main + test). 라운드 중 빠른 확인.
#   --with-test : 전체 테스트까지. 커밋 직전 1회(교차 파괴 최종 차단).
#
# 왜 gradle 전체 컴파일인가(단일 파일 javac 아니라):
#   CompileVerification(in-JVM javac)은 "자율 루프가 gradle 안에서 도는" 제약 때문에 생긴 우회였다.
#   통합 루프의 드라이버는 gradle 바깥에서 돌므로 중첩 락이 없고, 전체 compileJava가 엄격히 더 강하다
#   (단일 파일 javac는 '다른 파일을 깨뜨리는 수정'을 못 잡는다).
#
# 사용: bash scripts/review-verify.sh [--with-test]
# 반환: 0 = 통과. 1 = 실패(드라이버가 로그를 읽고 재수정 — 예산은 소모하지 않는다).
set -u

WITH_TEST=0
for a in "$@"; do
  [ "$a" = "--with-test" ] && WITH_TEST=1
done

# Windows 콘솔(cp949)에서 게이트 메시지 한글이 깨지지 않게 — 훅과 동일 처리.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dfile.encoding=UTF-8"

echo "[reviewVerify] 컴파일 검증(main + test) ..."
if ! ./gradlew -q compileJava compileTestJava --no-daemon; then
  echo "[reviewVerify] ❌ 컴파일 실패 → 수정이 코드를 깨뜨렸다. 위 오류를 보고 재수정할 것."
  exit 1
fi
echo "[reviewVerify] ✅ 컴파일 통과"

if [ "$WITH_TEST" -eq 1 ]; then
  echo "[reviewVerify] 전체 테스트 — 커밋 직전 최종 게이트 ..."
  if ! ./gradlew -q test --no-daemon; then
    echo "[reviewVerify] ❌ 테스트 실패 → 커밋하지 말 것. 실패 케이스를 보고 재수정."
    exit 1
  fi
  echo "[reviewVerify] ✅ 테스트 통과"
fi

echo "[reviewVerify] 검증 완료 — 커밋 가능"
exit 0
