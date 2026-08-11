package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import java.time.Clock;
import no.sikt.nva.monitoring.model.ChatbotCustomNotification;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class InspectorDigestHandler implements RequestHandler<ScheduledEvent, Void> {

  public static final String SLACK_SNS_TOPIC_ARN_ENV = "SLACK_SNS_TOPIC_ARN";
  public static final String NEW_FINDING_MAX_AGE_HOURS_ENV = "NEW_FINDING_MAX_AGE_HOURS";
  private static final Logger LOGGER = LoggerFactory.getLogger(InspectorDigestHandler.class);

  private final InspectorDigestService digestService;
  private final SnsClient snsClient;
  private final String slackSnsTopicArn;
  private final int newFindingMaxAgeHours;

  @JacocoGenerated
  public InspectorDigestHandler() {
    this(Inspector2Client.create(), SnsClient.create(), new Environment(), Clock.systemUTC());
  }

  public InspectorDigestHandler(
      Inspector2Client inspectorClient, SnsClient snsClient, Environment environment, Clock clock) {
    this.digestService = new InspectorDigestService(inspectorClient, clock);
    this.snsClient = snsClient;
    this.slackSnsTopicArn = environment.readEnv(SLACK_SNS_TOPIC_ARN_ENV);
    this.newFindingMaxAgeHours =
        Integer.parseInt(environment.readEnv(NEW_FINDING_MAX_AGE_HOURS_ENV));
  }

  @Override
  public Void handleRequest(ScheduledEvent scheduledEvent, Context context) {
    digestService
        .createDailyDigest(newFindingMaxAgeHours)
        .ifPresentOrElse(this::publish, InspectorDigestHandler::logNothingToReport);
    return null;
  }

  private void publish(ChatbotCustomNotification notification) {
    LOGGER.info("Publishing Inspector findings digest to Slack");
    snsClient.publish(
        PublishRequest.builder()
            .topicArn(slackSnsTopicArn)
            .message(notification.toJsonString())
            .build());
  }

  private static void logNothingToReport() {
    LOGGER.info("No new HIGH or CRITICAL package vulnerability findings, skipping digest");
  }
}
