package com.example.authcommon.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authcommon.config.CacheConfig;
import com.example.authcommon.user.AppUser;
import com.example.authcommon.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoSpyBean
  private AppUserRepository appUserRepository;

  @Autowired
  private CacheManager cacheManager;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void resetState() {
    appUserRepository.deleteAll();
    Cache cache = cacheManager.getCache(CacheConfig.AUTHORIZATION_CACHE);
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  void rejectsMissingJwt() throws Exception {
    mockMvc.perform(get("/api/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void allowsKubernetesReadinessProbeWithoutJwt() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness"))
      .andExpect(status().isOk());
  }

  @Test
  void rejectsAuthenticatedUserThatIsNotActiveInDatabase() throws Exception {
    appUserRepository.save(new AppUser("auth0|inactive", "inactive@example.com", false));

    mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt.claim(JwtClaimNames.SUB, "auth0|inactive"))))
      .andExpect(status().isForbidden());
  }

  @Test
  void allowsAuthenticatedUserThatIsActiveInDatabase() throws Exception {
    appUserRepository.save(new AppUser("auth0|123", "user@example.com", true));

    mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt.claim(JwtClaimNames.SUB, "auth0|123"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.userId", equalTo("auth0|123")))
      .andExpect(jsonPath("$.subject", equalTo("auth0|123")));
  }

  @Test
  void cachesDatabaseAuthorizationDecision() throws Exception {
    appUserRepository.save(new AppUser("auth0|cached", "cached@example.com", true));

    mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt.claim(JwtClaimNames.SUB, "auth0|cached"))))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt.claim(JwtClaimNames.SUB, "auth0|cached"))))
      .andExpect(status().isOk());

    verify(appUserRepository, times(1)).existsByAuth0UserIdAndActiveTrue("auth0|cached");
  }
}
