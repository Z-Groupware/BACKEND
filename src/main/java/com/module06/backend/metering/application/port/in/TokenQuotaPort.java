package com.module06.backend.metering.application.port.in;

import com.module06.backend.metering.application.result.QuotaStatusResult;

public interface TokenQuotaPort {

    QuotaStatusResult getStatus(Long companyId);
}
