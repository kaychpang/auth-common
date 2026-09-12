package com.example.authcommon.security;

import com.example.authcommon.config.Auth0Properties;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(Auth0Properties.class)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    DatabaseUserAuthorizationFilter databaseUserAuthorizationFilter,
    Auth0Properties auth0Properties
  ) throws Exception {
    return http
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(auth0Properties))))
      .addFilterAfter(databaseUserAuthorizationFilter, BearerTokenAuthenticationFilter.class)
      .build();
  }

  @Bean
  JwtDecoder jwtDecoder(
    org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties properties,
    Auth0Properties auth0Properties
  ) {
    String issuerUri = properties.getJwt().getIssuerUri();
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

    OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
    if (auth0Properties.hasAudience()) {
      jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        issuerValidator,
        new JwtAudienceValidator(List.of(auth0Properties.audience()))
      ));
    } else {
      jwtDecoder.setJwtValidator(issuerValidator);
    }

    return jwtDecoder;
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter(Auth0Properties auth0Properties) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(auth0Properties.userIdClaim());
    return converter;
  }
}
