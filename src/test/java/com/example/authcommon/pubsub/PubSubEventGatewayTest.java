package com.example.authcommon.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PubSubEventGatewayTest {

  private final PubSubProperties properties = new PubSubProperties(null, null);

  @Test
  void acknowledgesMessageAfterSuccessfulHandling() {
    PubSubEventHandler handler = mock(PubSubEventHandler.class);
    AckReplyConsumer consumer = mock(AckReplyConsumer.class);
    PubSubEventGateway gateway = new PubSubEventGateway(properties, handler);
    PubsubMessage message = PubsubMessage.newBuilder()
      .setMessageId("message-1")
      .setData(ByteString.copyFromUtf8("{\"type\":\"created\"}"))
      .putAttributes("trace-id", "trace-1")
      .build();

    gateway.receive(PubSubProject.PROJECT_ONE, message, consumer);

    ArgumentCaptor<PubSubEvent> eventCaptor = ArgumentCaptor.forClass(PubSubEvent.class);
    verify(handler).handle(eventCaptor.capture());
    PubSubEvent event = eventCaptor.getValue();
    assertThat(event.project()).isEqualTo(PubSubProject.PROJECT_ONE);
    assertThat(event.messageId()).isEqualTo("message-1");
    assertThat(event.payload()).isEqualTo("{\"type\":\"created\"}");
    assertThat(event.attributes()).containsEntry("trace-id", "trace-1");
    verify(consumer).ack();
    verify(consumer, never()).nack();
  }

  @Test
  void negativelyAcknowledgesMessageWhenHandlingFails() {
    PubSubEventHandler handler = mock(PubSubEventHandler.class);
    AckReplyConsumer consumer = mock(AckReplyConsumer.class);
    PubSubEventGateway gateway = new PubSubEventGateway(properties, handler);
    PubsubMessage message = PubsubMessage.newBuilder()
      .setMessageId("message-2")
      .setData(ByteString.copyFromUtf8("payload"))
      .build();
    RuntimeException failure = new RuntimeException("processing failed");
    org.mockito.Mockito.doThrow(failure).when(handler).handle(org.mockito.ArgumentMatchers.any());

    gateway.receive(PubSubProject.PROJECT_TWO, message, consumer);

    verify(consumer).nack();
    verify(consumer, never()).ack();
  }
}
