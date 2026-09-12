package com.example.authcommon.pubsub;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pubsub")
public record PubSubProperties(Project projectOne, Project projectTwo) {

  public record Project(
    boolean enabled,
    String projectId,
    String topicId,
    String subscriptionId,
    String credentialsJson
  ) {

    void validate(String propertyPrefix) {
      if (!enabled) {
        return;
      }
      requireText(projectId, propertyPrefix + ".project-id");
      requireText(topicId, propertyPrefix + ".topic-id");
      requireText(subscriptionId, propertyPrefix + ".subscription-id");
    }

    private static void requireText(String value, String propertyName) {
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(propertyName + " must be set when the Pub/Sub project is enabled");
      }
    }

    @Override
    public String toString() {
      return "Project[enabled=" + enabled
        + ", projectId=" + projectId
        + ", topicId=" + topicId
        + ", subscriptionId=" + subscriptionId
        + ", credentialsJson=<redacted>]";
    }
  }
}
