package com.module06.backend.metering.application.port.in;

import com.module06.backend.metering.application.command.RecordTokenUsageCommand;

public interface RecordTokenUsagePort {

    void record(RecordTokenUsageCommand command);
}
