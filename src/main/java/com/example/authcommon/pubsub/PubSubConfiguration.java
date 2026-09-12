package com.example.authcommon.pubsub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PubSubProperties.class)
class PubSubConfiguration {

  @Bean
  @ConditionalOnMissingBean(PubSubEventHandler.class)
  PubSubEventHandler applicationEventPubSubHandler(ApplicationEventPublisher eventPublisher) {
    return eventPublisher::publishEvent;
  }
}
