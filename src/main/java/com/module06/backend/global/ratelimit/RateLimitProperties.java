package com.module06.backend.global.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

/**
 * 공개 엔드포인트별 제한값.
 *
 * <p><b>왜 엔드포인트마다 다른가</b> — 한 회사의 직원 수십 명이 사무실 IP 하나를 공유한다.
 * 전 경로에 같은 숫자를 걸면 공격자에게 넉넉하거나 사무실을 막거나 둘 중 하나가 된다.
 * 그래서 "사람이 몰릴 수 있는 경로"와 "공격자만 두들기는 경로"를 나눈다.
 *
 * @param loginPerIp        로그인. 아침 출근 시간에 한 사무실에서 몰린다. IP 는 넉넉히 두고,
 *                          진짜 방어는 {@code loginPerAccount} 가 한다.
 * @param loginPerAccount   같은 계정을 노린 시도. 실패만 센다 — 여기가 무차별 대입 방어의 본체다.
 * @param refreshPerIp      재발급. 액세스 토큰(30분)이 만료될 때마다 사람 수만큼 나가므로 가장 넉넉하다.
 *                          그래도 걸어 두는 이유는 permitAll 인 데다 BCrypt 비용도 없어 가장 싸게
 *                          두들길 수 있는 경로이기 때문이다.
 * @param companyLookupPerIp 기업코드 조회. {@code CompanyCodeGenerator} javadoc 이 전수 탐색
 *                          난이도를 계산할 때 <b>"IP 당 분당 20회"를 전제</b>한다 — 그 전제를
 *                          실제로 만드는 값이라 함부로 올리면 그 계산이 무너진다.
 * @param registrationPerIp 기업 등록. 한 번 성공하면 회사와 오너 계정이 생기고 되돌릴 경로가 없다.
 *                          사람이 반복할 일이 없는 행위라 가장 좁게 잡는다.
 * @param passwordChangePerMember 마이페이지 비밀번호 변경. 인증이 필요한 경로인데도 제한을 거는
 *                          유일한 자리다 — 여기는 <b>현재 비밀번호를 반복해 넣어 볼 수 있는</b>
 *                          곳이라, 액세스 토큰 하나를 훔친 사람이 원래 비밀번호를 알아내는 통로가
 *                          된다. 로그인과 같은 값(5회/5분)을 쓰고, 마찬가지로 실패만 센다.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        Rule loginPerIp,
        Rule loginPerAccount,
        Rule refreshPerIp,
        Rule companyLookupPerIp,
        Rule registrationPerIp,
        Rule passwordChangePerMember
) {

    public record Rule(int limit, Duration window) {

        public RateLimitPolicy asPolicy(String name) {
            return new RateLimitPolicy(name, limit, window);
        }
    }

    public RateLimitPolicy loginAccountPolicy() {
        return loginPerAccount.asPolicy("login-account");
    }

    public RateLimitPolicy passwordChangePolicy() {
        return passwordChangePerMember.asPolicy("password-change");
    }

    /**
     * IP 기준 제한을 걸 경로 목록. 필터가 이 순서로 훑어 첫 일치를 쓴다.
     *
     * <p>여기 없는 경로는 제한이 없다. 인증이 필요한 경로를 넣지 않는 것은 의도다 — 그쪽은
     * 토큰이 있어야 도달하므로 익명 대량 요청의 표면이 아니고, 걸면 정상 사용자만 막는다.
     */
    public List<IpRule> ipRules() {
        return List.of(
                new IpRule(HttpMethod.POST, "/api/auth/login", loginPerIp.asPolicy("login-ip")),
                new IpRule(HttpMethod.POST, "/api/auth/refresh", refreshPerIp.asPolicy("refresh-ip")),
                new IpRule(HttpMethod.POST, "/api/companies/lookup", companyLookupPerIp.asPolicy("company-lookup-ip")),
                new IpRule(HttpMethod.POST, "/api/companies/registrations", registrationPerIp.asPolicy("registration-ip")));
    }

    public record IpRule(HttpMethod method, String path, RateLimitPolicy policy) {

        public boolean matches(String requestMethod, String requestPath) {
            return method.matches(requestMethod) && path.equals(requestPath);
        }
    }
}
