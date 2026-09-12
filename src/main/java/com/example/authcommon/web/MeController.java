package com.example.authcommon.web;

import com.example.authcommon.config.Auth0Properties;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

  private final Auth0Properties auth0Properties;

  public MeController(Auth0Properties auth0Properties) {
    this.auth0Properties = auth0Properties;
  }

  @GetMapping("/api/me")
  Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
      "userId", jwt.getClaimAsString(auth0Properties.userIdClaim()),
      "subject", jwt.getSubject()
    );
  }
}
