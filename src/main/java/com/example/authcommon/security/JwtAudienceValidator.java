package com.example.authcommon.security;

import java.util.Collection;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
    "invalid_token",
    "The required audience is missing.",
    null
  );

  private final Collection<String> requiredAudiences;

  JwtAudienceValidator(Collection<String> requiredAudiences) {
    this.requiredAudiences = requiredAudiences;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (token.getAudience().containsAll(requiredAudiences)) {
      return OAuth2TokenValidatorResult.success();
    }

    return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
  }
}
