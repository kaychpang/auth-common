package com.example.authcommon.security;

import com.example.authcommon.config.Auth0Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DatabaseUserAuthorizationFilter extends OncePerRequestFilter {

  private final DatabaseUserAuthorizationService authorizationService;
  private final Auth0Properties auth0Properties;

  public DatabaseUserAuthorizationFilter(
    DatabaseUserAuthorizationService authorizationService,
    Auth0Properties auth0Properties
  ) {
    this.authorizationService = authorizationService;
    this.auth0Properties = auth0Properties;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      filterChain.doFilter(request, response);
      return;
    }

    String userId = jwt.getClaimAsString(auth0Properties.userIdClaim());
    if (userId == null || userId.isBlank() || !authorizationService.isAuthorized(userId)) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Authenticated user is not authorized.");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
