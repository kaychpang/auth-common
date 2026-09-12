package com.example.authcommon.security;

import static com.example.authcommon.config.CacheConfig.AUTHORIZATION_CACHE;

import com.example.authcommon.user.AppUserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserAuthorizationService {

  private final AppUserRepository appUserRepository;

  public DatabaseUserAuthorizationService(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Cacheable(cacheNames = AUTHORIZATION_CACHE, unless = "#auth0UserId == null || #auth0UserId.isBlank()")
  @Transactional(readOnly = true)
  public boolean isAuthorized(String auth0UserId) {
    return appUserRepository.existsByAuth0UserIdAndActiveTrue(auth0UserId);
  }
}
