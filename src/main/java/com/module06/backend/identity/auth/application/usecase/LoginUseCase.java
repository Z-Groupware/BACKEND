package com.module06.backend.identity.auth.application.usecase;

import com.module06.backend.identity.auth.application.command.LoginCommand;
import com.module06.backend.identity.auth.application.dto.LoginResult;

public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
