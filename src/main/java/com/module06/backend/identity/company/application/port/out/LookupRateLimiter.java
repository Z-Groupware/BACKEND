package com.module06.backend.identity.company.application.port.out;

public interface LookupRateLimiter {

    void checkOrThrow(String clientIp);
}
