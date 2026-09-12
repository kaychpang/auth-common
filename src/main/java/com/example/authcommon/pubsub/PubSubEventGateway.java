package com.example.authcommon.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class PubSubEventGateway implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PubSubEventGateway.class);
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

  private final PubSubProperties properties;
  private final PubSubEventHandler eventHandler;
  private final Map<PubSubProject, Publisher> publishers = new ConcurrentHashMap<>();
  private final Map<PubSubProject, Subscriber> subscribers = new ConcurrentHashMap<>();
  private volatile boolean running;

  public PubSubEventGateway(PubSubProperties properties, PubSubEventHandler eventHandler) {
    this.properties = properties;
    this.eventHandler = eventHandler;
  }

  public CompletableFuture<String> publish(PubSubProject project, String payload) {
    Objects.requireNonNull(project, "project must not be null");
    Objects.requireNonNull(payload, "payload must not be null");

    Publisher publisher = publishers.get(project);
    if (publisher == null) {
      return CompletableFuture.failedFuture(
        new IllegalStateException("Pub/Sub publisher is not running for " + project)
      );
    }

    PubsubMessage message = PubsubMessage.newBuilder()
      .setData(ByteString.copyFromUtf8(payload))
      .build();
    return toCompletableFuture(publisher.publish(message));
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }

    try {
      startProject(PubSubProject.PROJECT_ONE, properties.projectOne(), "app.pubsub.project-one");
      startProject(PubSubProject.PROJECT_TWO, properties.projectTwo(), "app.pubsub.project-two");
      running = true;
    } catch (Exception exception) {
      stop();
      throw new IllegalStateException("Unable to start GCP Pub/Sub clients", exception);
    }
  }

  private void startProject(
    PubSubProject project,
    PubSubProperties.Project configuration,
    String propertyPrefix
  ) throws IOException {
    if (configuration == null || !configuration.enabled()) {
      return;
    }
    configuration.validate(propertyPrefix);

    FixedCredentialsProvider credentialsProvider = credentialsProvider(configuration.credentialsJson());
    Publisher publisher = Publisher.newBuilder(TopicName.of(configuration.projectId(), configuration.topicId()))
      .setCredentialsProvider(credentialsProvider)
      .build();
    publishers.put(project, publisher);

    MessageReceiver receiver = (message, consumer) -> receive(project, message, consumer);
    Subscriber subscriber = Subscriber.newBuilder(
        ProjectSubscriptionName.of(configuration.projectId(), configuration.subscriptionId()),
        receiver
      )
      .setCredentialsProvider(credentialsProvider)
      .build();
    subscribers.put(project, subscriber);
    subscriber.startAsync().awaitRunning(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    log.info("Started Pub/Sub publisher and subscriber for {}", project);
  }

  void receive(PubSubProject project, PubsubMessage message, AckReplyConsumer consumer) {
    PubSubEvent event = new PubSubEvent(
      project,
      message.getMessageId(),
      message.getData().toStringUtf8(),
      Map.copyOf(message.getAttributesMap())
    );

    try {
      eventHandler.handle(event);
      consumer.ack();
    } catch (Exception exception) {
      log.error("Failed to process Pub/Sub message {} from {}", message.getMessageId(), project, exception);
      consumer.nack();
    }
  }

  private FixedCredentialsProvider credentialsProvider(String credentialsJson) throws IOException {
    GoogleCredentials credentials;
    if (credentialsJson == null || credentialsJson.isBlank()) {
      credentials = GoogleCredentials.getApplicationDefault();
    } else {
      credentials = GoogleCredentials.fromStream(
        new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))
      );
    }
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
    }
    return FixedCredentialsProvider.create(credentials);
  }

  private CompletableFuture<String> toCompletableFuture(ApiFuture<String> apiFuture) {
    CompletableFuture<String> result = new CompletableFuture<>();
    ApiFutures.addCallback(apiFuture, new ApiFutureCallback<>() {
      @Override
      public void onFailure(Throwable throwable) {
        result.completeExceptionally(throwable);
      }

      @Override
      public void onSuccess(String messageId) {
        result.complete(messageId);
      }
    }, Runnable::run);
    return result;
  }

  @Override
  public synchronized void stop() {
    subscribers.values().forEach(Subscriber::stopAsync);
    subscribers.values().forEach(subscriber -> {
      try {
        subscriber.awaitTerminated(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (Exception exception) {
        log.warn("Timed out while stopping a Pub/Sub subscriber", exception);
      }
    });
    subscribers.clear();

    publishers.values().forEach(Publisher::shutdown);
    publishers.values().forEach(publisher -> {
      try {
        publisher.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    });
    publishers.clear();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
