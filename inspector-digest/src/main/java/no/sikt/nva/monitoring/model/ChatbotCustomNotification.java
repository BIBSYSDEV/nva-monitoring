package no.sikt.nva.monitoring.model;

import no.unit.nva.commons.json.JsonSerializable;

/**
 * AWS Chatbot custom notification envelope. Chatbot silently drops SNS messages that do not match
 * this schema, so the field values must stay aligned with the InputTransformer used by
 * InspectorFindingEventRule in template.yaml.
 */
public record ChatbotCustomNotification(String version, String source, Content content)
    implements JsonSerializable {

  public static final String SCHEMA_VERSION = "1.0";
  public static final String CUSTOM_SOURCE = "custom";
  public static final String CLIENT_MARKDOWN_TEXT_TYPE = "client-markdown";

  public static ChatbotCustomNotification create(String title, String description) {
    return new ChatbotCustomNotification(
        SCHEMA_VERSION, CUSTOM_SOURCE, new Content(CLIENT_MARKDOWN_TEXT_TYPE, title, description));
  }

  public record Content(String textType, String title, String description) {}
}
