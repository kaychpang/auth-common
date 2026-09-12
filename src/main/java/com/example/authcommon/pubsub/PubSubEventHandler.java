package com.example.authcommon.pubsub;

@FunctionalInterface
public interface PubSubEventHandler {

  void handle(PubSubEvent event);
}
