package no.sikt.nva.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import no.sikt.nva.monitoring.model.ChatbotCustomNotification;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.AwsLambdaFunctionDetails;
import software.amazon.awssdk.services.inspector2.model.Finding;
import software.amazon.awssdk.services.inspector2.model.FindingStatus;
import software.amazon.awssdk.services.inspector2.model.FindingType;
import software.amazon.awssdk.services.inspector2.model.FixAvailable;
import software.amazon.awssdk.services.inspector2.model.ListFindingsRequest;
import software.amazon.awssdk.services.inspector2.model.ListFindingsResponse;
import software.amazon.awssdk.services.inspector2.model.PackageVulnerabilityDetails;
import software.amazon.awssdk.services.inspector2.model.Resource;
import software.amazon.awssdk.services.inspector2.model.ResourceDetails;
import software.amazon.awssdk.services.inspector2.model.ResourceType;
import software.amazon.awssdk.services.inspector2.model.Severity;
import software.amazon.awssdk.services.inspector2.model.StringFilter;
import software.amazon.awssdk.services.inspector2.model.VulnerablePackage;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

class InspectorDigestHandlerTest {

  private static final Environment ENVIRONMENT = new Environment();
  private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Instant RECENTLY_OBSERVED = NOW.minus(Duration.ofHours(2));
  private static final Instant OBSERVED_LONG_AGO = NOW.minus(Duration.ofDays(10));
  private static final Context CONTEXT = mock(Context.class);
  private static final ScheduledEvent EVENT = new ScheduledEvent();
  private static final String NO_FIXED_VERSION = null;

  private Inspector2Client inspectorClient;
  private SnsClient snsClient;

  @BeforeEach
  void init() {
    inspectorClient = mock(Inspector2Client.class);
    snsClient = mock(SnsClient.class);
  }

  @Test
  void shouldNotPublishWhenThereAreNoNewFindings() {
    stubSinglePage(
        finding(
            "CVE-2020-0001",
            "log4j-core",
            "2.14.0",
            Severity.HIGH,
            OBSERVED_LONG_AGO,
            FixAvailable.YES,
            "2.17.1",
            "function-one"),
        findingWithoutFirstObservedAt());

    handler().handleRequest(EVENT, CONTEXT);

    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishChatbotCustomNotificationToConfiguredTopic() {
    stubSinglePage(criticalFinding("CVE-2026-1111", "function-one"));

    handler().handleRequest(EVENT, CONTEXT);

    var notification = publishedNotification();
    assertThat(notification.version()).isEqualTo(ChatbotCustomNotification.SCHEMA_VERSION);
    assertThat(notification.source()).isEqualTo(ChatbotCustomNotification.CUSTOM_SOURCE);
    assertThat(notification.content().textType())
        .isEqualTo(ChatbotCustomNotification.CLIENT_MARKDOWN_TEXT_TYPE);
    assertThat(notification.content().title()).contains("1 new");
  }

  @Test
  void shouldAggregateNewFindingsByVulnerabilityAndPackage() {
    stubSinglePage(
        criticalFinding("CVE-2026-1111", "function-one"),
        criticalFinding("CVE-2026-1111", "function-two"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(description).containsOnlyOnce("CVE-2026-1111");
    assertThat(lineContaining(description, "CVE-2026-1111")).contains("(2 stacks)");
  }

  @Test
  void shouldListActiveVulnerabilityCountsPerStackIncludingOldFindings() {
    stubSinglePage(
        criticalFinding("CVE-2026-1111", "function-one"),
        criticalFinding("CVE-2026-1111", "function-two"),
        finding(
            "CVE-2020-0001",
            "jackson-databind",
            "2.9.0",
            Severity.HIGH,
            OBSERVED_LONG_AGO,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-three"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(description)
        .contains("• `function-one`: 1 CRITICAL")
        .contains("• `function-two`: 1 CRITICAL")
        .contains("• `function-three`: 1 HIGH");
    assertThat(lineContaining(description, "New in the last")).contains("24 hours");
    assertThat(description).doesNotContain("CVE-2020-0001");
  }

  @Test
  void shouldCountVulnerabilityOnceAtItsHighestSeverityWithinEachStack() {
    stubSinglePage(
        highFinding("CVE-2026-1111", "function-one"),
        criticalFinding("CVE-2026-1111", "function-one"),
        criticalFinding("CVE-2026-2222", "function-two"),
        highFinding("CVE-2026-2222", "function-two"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "`function-one`"))
        .contains("1 CRITICAL")
        .doesNotContain("HIGH");
    assertThat(lineContaining(description, "`function-two`"))
        .contains("1 CRITICAL")
        .doesNotContain("HIGH");
  }

  @Test
  void shouldListStacksWithCriticalVulnerabilitiesFirstThenByHighCount() {
    stubSinglePage(
        highFinding("CVE-2026-0001", "stack-one-high", "stack-two-highs"),
        highFinding("CVE-2026-0002", "stack-two-highs"),
        criticalFinding("CVE-2026-9999", "stack-critical"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "`stack-two-highs`")).contains("2 HIGH");
    assertThat(description.indexOf("stack-critical"))
        .isLessThan(description.indexOf("stack-two-highs"));
    assertThat(description.indexOf("stack-two-highs"))
        .isLessThan(description.indexOf("stack-one-high"));
  }

  @Test
  void shouldTruncateStackListWhenThereAreTooManyAffectedStacks() {
    var stackNames =
        IntStream.rangeClosed(1, 12).mapToObj("service-%02d"::formatted).toArray(String[]::new);
    stubSinglePage(criticalFinding("CVE-2026-1111", stackNames));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(description)
        .contains("service-10")
        .contains("...and 2 more")
        .doesNotContain("service-11")
        .doesNotContain("service-12");
  }

  @Test
  void shouldIncludeFixedVersionOnlyWhenFixIsAvailable() {
    stubSinglePage(
        finding(
            "CVE-2026-1111",
            "log4j-core",
            "2.14.0",
            Severity.CRITICAL,
            RECENTLY_OBSERVED,
            FixAvailable.YES,
            "2.17.1",
            "function-one"),
        finding(
            "CVE-2026-2222",
            "commons-text",
            "1.9",
            Severity.HIGH,
            RECENTLY_OBSERVED,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-one"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "CVE-2026-1111"))
        .contains("`log4j-core 2.14.0`")
        .contains("fix: 2.17.1");
    assertThat(lineContaining(description, "CVE-2026-2222")).doesNotContain("fix:");
  }

  @Test
  void shouldListCriticalVulnerabilitiesBeforeHighOnes() {
    stubSinglePage(
        finding(
            "CVE-2026-0001",
            "commons-text",
            "1.9",
            Severity.HIGH,
            RECENTLY_OBSERVED,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-one"),
        criticalFinding("CVE-2026-9999", "function-one"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(description.indexOf("CVE-2026-9999"))
        .isLessThan(description.indexOf("CVE-2026-0001"));
  }

  @Test
  void shouldUseHighestSeverityWhenSameVulnerabilityHasMixedSeverities() {
    stubSinglePage(
        finding(
            "CVE-2026-1111",
            "log4j-core",
            "2.14.0",
            Severity.HIGH,
            RECENTLY_OBSERVED,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-one"),
        finding(
            "CVE-2026-1111",
            "log4j-core",
            "2.14.0",
            Severity.CRITICAL,
            RECENTLY_OBSERVED,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-two"));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "CVE-2026-1111")).contains("CRITICAL");
  }

  @Test
  void shouldFetchAllPagesAndFilterOnActiveHighAndCriticalPackageVulnerabilities() {
    var firstPage =
        ListFindingsResponse.builder()
            .findings(criticalFinding("CVE-2026-1111", "function-one"))
            .nextToken("next-page-token")
            .build();
    var secondPage =
        ListFindingsResponse.builder()
            .findings(criticalFinding("CVE-2026-2222", "function-two"))
            .build();
    when(inspectorClient.listFindings(any(ListFindingsRequest.class)))
        .thenReturn(firstPage, secondPage);

    handler().handleRequest(EVENT, CONTEXT);

    var requestCaptor = ArgumentCaptor.forClass(ListFindingsRequest.class);
    verify(inspectorClient, times(2)).listFindings(requestCaptor.capture());
    assertThat(requestCaptor.getAllValues().get(1).nextToken()).isEqualTo("next-page-token");
    var filterCriteria = requestCaptor.getAllValues().getFirst().filterCriteria();
    assertThat(filterValues(filterCriteria.findingStatus())).containsExactly("ACTIVE");
    assertThat(filterValues(filterCriteria.findingType())).containsExactly("PACKAGE_VULNERABILITY");
    assertThat(filterValues(filterCriteria.severity()))
        .containsExactlyInAnyOrder("HIGH", "CRITICAL");

    var description = publishedNotification().content().description();
    assertThat(description).contains("CVE-2026-1111").contains("CVE-2026-2222");
  }

  @Test
  void shouldTruncateVulnerabilityListWhenThereAreTooManyNewFindings() {
    var findings =
        IntStream.rangeClosed(1, 7)
            .mapToObj(index -> criticalFinding("CVE-2026-%04d".formatted(index), "function-one"))
            .toArray(Finding[]::new);
    stubSinglePage(findings);

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(description).contains("CVE-2026-0005").contains("...and 2 more");
    assertThat(description).doesNotContain("CVE-2026-0006").doesNotContain("CVE-2026-0007");
  }

  @Test
  void shouldCountScannedVersionsOfTheSameFunctionAsOneFunction() {
    var unqualifiedArn = "arn:aws:lambda:eu-west-1:123456789012:function:function-a";
    stubSinglePage(
        withResource(
            criticalFinding("CVE-2026-1111", "unused"),
            lambdaResource(unqualifiedArn + ":$LATEST", "function-a")),
        withResource(
            criticalFinding("CVE-2026-1111", "unused"),
            lambdaResource(unqualifiedArn + ":12", "function-a")),
        withResource(
            criticalFinding("CVE-2026-1111", "unused"),
            lambdaResource(unqualifiedArn + ":13", "function-a")));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "CVE-2026-1111")).contains("(1 stacks)");
    assertThat(description).containsOnlyOnce("• `function-a`: 1 CRITICAL");
  }

  @Test
  void shouldCountFunctionsInTheSameStackAsOneStack() {
    stubSinglePage(
        withResource(
            criticalFinding("CVE-2026-1111", "unused"),
            stackTaggedResource("function-one", "nva-cristin-service")),
        withResource(
            criticalFinding("CVE-2026-1111", "unused"),
            stackTaggedResource("function-two", "nva-cristin-service")));

    handler().handleRequest(EVENT, CONTEXT);

    var description = publishedNotification().content().description();
    assertThat(lineContaining(description, "CVE-2026-1111")).contains("(1 stacks)");
    assertThat(description).containsOnlyOnce("• `nva-cristin-service`: 1 CRITICAL");
  }

  private static Resource stackTaggedResource(String id, String stackName) {
    return Resource.builder()
        .id(id)
        .type(ResourceType.AWS_LAMBDA_FUNCTION)
        .tags(Map.of("aws:cloudformation:stack-name", stackName))
        .build();
  }

  private static Finding withResource(Finding finding, Resource resource) {
    return finding.toBuilder().resources(resource).build();
  }

  private static Resource lambdaResource(String versionQualifiedArn, String functionName) {
    return Resource.builder()
        .id(versionQualifiedArn)
        .type(ResourceType.AWS_LAMBDA_FUNCTION)
        .details(resourceDetails(functionName))
        .build();
  }

  private static ResourceDetails resourceDetails(String functionName) {
    return ResourceDetails.builder()
        .awsLambdaFunction(AwsLambdaFunctionDetails.builder().functionName(functionName).build())
        .build();
  }

  @Test
  void shouldNotReportOldVulnerabilityAsNewWhenRedeployCreatesFreshFinding() {
    stubSinglePage(
        finding(
            "CVE-2020-0001",
            "log4j-core",
            "2.14.0",
            Severity.HIGH,
            OBSERVED_LONG_AGO,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-one"),
        finding(
            "CVE-2020-0001",
            "log4j-core",
            "2.14.0",
            Severity.HIGH,
            RECENTLY_OBSERVED,
            FixAvailable.NO,
            NO_FIXED_VERSION,
            "function-two"));

    handler().handleRequest(EVENT, CONTEXT);

    verify(snsClient, never()).publish(any(PublishRequest.class));
  }

  private InspectorDigestHandler handler() {
    return new InspectorDigestHandler(inspectorClient, snsClient, ENVIRONMENT, FIXED_CLOCK);
  }

  private void stubSinglePage(Finding... findings) {
    var response = ListFindingsResponse.builder().findings(findings).build();
    when(inspectorClient.listFindings(any(ListFindingsRequest.class))).thenReturn(response);
  }

  private ChatbotCustomNotification publishedNotification() {
    var publishCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(publishCaptor.capture());
    try {
      return JsonUtils.dtoObjectMapper.readValue(
          publishCaptor.getValue().message(), ChatbotCustomNotification.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String lineContaining(String description, String expectedContent) {
    return Arrays.stream(description.split("\n"))
        .filter(line -> line.contains(expectedContent))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> filterValues(List<StringFilter> filters) {
    return filters.stream().map(StringFilter::value).toList();
  }

  private static Finding criticalFinding(String vulnerabilityId, String... functionIds) {
    return finding(
        vulnerabilityId,
        "log4j-core",
        "2.14.0",
        Severity.CRITICAL,
        RECENTLY_OBSERVED,
        FixAvailable.YES,
        "2.17.1",
        functionIds);
  }

  private static Finding highFinding(String vulnerabilityId, String... functionIds) {
    return finding(
        vulnerabilityId,
        "log4j-core",
        "2.14.0",
        Severity.HIGH,
        RECENTLY_OBSERVED,
        FixAvailable.NO,
        NO_FIXED_VERSION,
        functionIds);
  }

  private static Finding findingWithoutFirstObservedAt() {
    return finding(
        "CVE-2020-0002",
        "guava",
        "19.0",
        Severity.HIGH,
        null,
        FixAvailable.NO,
        NO_FIXED_VERSION,
        "function-one");
  }

  private static Finding finding(
      String vulnerabilityId,
      String packageName,
      String packageVersion,
      Severity severity,
      Instant firstObservedAt,
      FixAvailable fixAvailable,
      String fixedVersion,
      String... functionIds) {
    var vulnerablePackage =
        VulnerablePackage.builder()
            .name(packageName)
            .version(packageVersion)
            .fixedInVersion(fixedVersion)
            .build();
    var resources =
        Arrays.stream(functionIds)
            .map(id -> Resource.builder().id(id).type(ResourceType.AWS_LAMBDA_FUNCTION).build())
            .toList();
    return Finding.builder()
        .severity(severity)
        .status(FindingStatus.ACTIVE)
        .type(FindingType.PACKAGE_VULNERABILITY)
        .firstObservedAt(firstObservedAt)
        .fixAvailable(fixAvailable)
        .packageVulnerabilityDetails(
            PackageVulnerabilityDetails.builder()
                .vulnerabilityId(vulnerabilityId)
                .vulnerablePackages(vulnerablePackage)
                .build())
        .resources(resources)
        .build();
  }
}
