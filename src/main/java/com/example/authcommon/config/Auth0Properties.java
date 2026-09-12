package com.example.authcommon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth0")
public record Auth0Properties(
  String audience,
  String userIdClaim,
  String clientId,
  String clientSecret
) {

  public Auth0Properties {
    if (userIdClaim == null || userIdClaim.isBlank()) {
      userIdClaim = "sub";
    }
  }

  public boolean hasAudience() {
    return audience != null && !audience.isBlank();
  }
}
