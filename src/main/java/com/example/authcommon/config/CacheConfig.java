package com.example.authcommon.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilderCustomizer;

@Configuration
@EnableConfigurationProperties(CacheConfig.AuthorizationCacheProperties.class)
public class CacheConfig implements CachingConfigurer {

  public static final String AUTHORIZATION_CACHE = "authorization-decisions";

  private final AuthorizationCacheProperties authorizationCacheProperties;

  public CacheConfig(AuthorizationCacheProperties authorizationCacheProperties) {
    this.authorizationCacheProperties = authorizationCacheProperties;
  }

  @Bean
  RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
    return builder -> builder.withCacheConfiguration(
      AUTHORIZATION_CACHE,
      RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(authorizationCacheProperties.ttl())
        .disableCachingNullValues()
    );
  }

  @Bean
  @Override
  public KeyGenerator keyGenerator() {
    return (target, method, params) -> {
      if (params.length == 1 && params[0] instanceof String value) {
        return value.trim();
      }
      return org.springframework.cache.interceptor.SimpleKeyGenerator.generateKey(params);
    };
  }

  @ConfigurationProperties(prefix = "app.authorization-cache")
  public record AuthorizationCacheProperties(Duration ttl) {

    public AuthorizationCacheProperties {
      if (ttl == null) {
        ttl = Duration.ofMinutes(5);
      }
    }
  }
}
