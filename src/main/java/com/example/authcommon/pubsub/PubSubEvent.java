package com.example.authcommon.pubsub;

import java.util.Map;

public record PubSubEvent(
  PubSubProject project,
  String messageId,
  String payload,
  Map<String, String> attributes
) {
}
