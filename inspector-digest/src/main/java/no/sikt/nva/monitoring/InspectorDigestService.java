package no.sikt.nva.monitoring;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.monitoring.model.AggregationKey;
import no.sikt.nva.monitoring.model.ChatbotCustomNotification;
import no.sikt.nva.monitoring.model.VulnerabilityAggregate;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.AwsLambdaFunctionDetails;
import software.amazon.awssdk.services.inspector2.model.FilterCriteria;
import software.amazon.awssdk.services.inspector2.model.Finding;
import software.amazon.awssdk.services.inspector2.model.FindingStatus;
import software.amazon.awssdk.services.inspector2.model.FindingType;
import software.amazon.awssdk.services.inspector2.model.FixAvailable;
import software.amazon.awssdk.services.inspector2.model.ListFindingsRequest;
import software.amazon.awssdk.services.inspector2.model.PackageVulnerabilityDetails;
import software.amazon.awssdk.services.inspector2.model.Resource;
import software.amazon.awssdk.services.inspector2.model.ResourceDetails;
import software.amazon.awssdk.services.inspector2.model.Severity;
import software.amazon.awssdk.services.inspector2.model.StringComparison;
import software.amazon.awssdk.services.inspector2.model.StringFilter;
import software.amazon.awssdk.services.inspector2.model.VulnerablePackage;

/**
 * Builds the Inspector findings digest: fetches all active HIGH and CRITICAL package vulnerability
 * findings, and summarizes them as one Slack message with headline totals plus the new
 * vulnerabilities aggregated per vulnerability and package. Returns nothing when no vulnerability
 * was first observed within the max-age window. The digest frequency is owned by the EventBridge
 * schedule in template.yaml, together with the matching max-age window.
 */
public class InspectorDigestService {

  public static final int MAX_VULNERABILITY_LINES = 15;
  private static final String UNKNOWN = "unknown";
  private static final String NO_FIXED_VERSION = "";
  private static final String ACTIVE_FINDINGS_HEADER =
      "*Active HIGH and CRITICAL dependency vulnerabilities:*";
  private static final String STACK_NAME_TAG = "aws:cloudformation:stack-name";
  private static final String LINE_BREAK = "\n";

  private final Inspector2Client inspectorClient;
  private final Clock clock;

  public InspectorDigestService(Inspector2Client inspectorClient, Clock clock) {
    this.inspectorClient = inspectorClient;
    this.clock = clock;
  }

  public Optional<ChatbotCustomNotification> createDigest(int newFindingMaxAgeHours) {
    var activeFindings = fetchActiveFindings();
    var newVulnerabilities = newVulnerabilities(activeFindings, newFindingMaxAgeHours);
    if (newVulnerabilities.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        createNotification(activeFindings, newVulnerabilities, newFindingMaxAgeHours));
  }

  private List<Finding> fetchActiveFindings() {
    var findings = new ArrayList<Finding>();
    String nextToken = null;
    do {
      var response = inspectorClient.listFindings(listFindingsRequest(nextToken));
      findings.addAll(response.findings());
      nextToken = response.nextToken();
    } while (nonNull(nextToken));
    return findings;
  }

  /**
   * A vulnerability counts as new when the earliest firstObservedAt across all its active findings
   * is within the max-age window. Deciding per finding would re-report old vulnerabilities, since
   * Inspector opens a fresh finding whenever an affected function is redeployed.
   */
  private List<VulnerabilityAggregate> newVulnerabilities(
      List<Finding> activeFindings, int newFindingMaxAgeHours) {
    var cutoff = Instant.now(clock).minus(Duration.ofHours(newFindingMaxAgeHours));
    var groups =
        activeFindings.stream()
            .collect(
                groupingBy(InspectorDigestService::aggregationKey, LinkedHashMap::new, toList()));
    return groups.entrySet().stream()
        .filter(entry -> vulnerabilityFirstObservedAfter(entry.getValue(), cutoff))
        .map(entry -> toAggregate(entry.getKey(), entry.getValue()))
        .sorted(displayOrder())
        .toList();
  }

  private static boolean vulnerabilityFirstObservedAfter(
      List<Finding> groupFindings, Instant cutoff) {
    return groupFindings.stream()
        .map(Finding::firstObservedAt)
        .filter(Objects::nonNull)
        .min(Instant::compareTo)
        .filter(cutoff::isBefore)
        .isPresent();
  }

  private static ListFindingsRequest listFindingsRequest(String nextToken) {
    return ListFindingsRequest.builder()
        .filterCriteria(highAndCriticalPackageVulnerabilities())
        .nextToken(nextToken)
        .build();
  }

  private static FilterCriteria highAndCriticalPackageVulnerabilities() {
    return FilterCriteria.builder()
        .findingStatus(equalsFilter(FindingStatus.ACTIVE.toString()))
        .findingType(equalsFilter(FindingType.PACKAGE_VULNERABILITY.toString()))
        .severity(
            equalsFilter(Severity.HIGH.toString()), equalsFilter(Severity.CRITICAL.toString()))
        .build();
  }

  private static StringFilter equalsFilter(String value) {
    return StringFilter.builder().comparison(StringComparison.EQUALS).value(value).build();
  }

  private static ChatbotCustomNotification createNotification(
      List<Finding> activeFindings,
      List<VulnerabilityAggregate> newVulnerabilities,
      int newFindingMaxAgeHours) {
    return ChatbotCustomNotification.create(
        title(newVulnerabilities),
        description(activeFindings, newVulnerabilities, newFindingMaxAgeHours));
  }

  private static String title(List<VulnerabilityAggregate> newVulnerabilities) {
    return ":shield: Inspector findings digest: %d new vulnerabilities"
        .formatted(newVulnerabilities.size());
  }

  private static String description(
      List<Finding> activeFindings,
      List<VulnerabilityAggregate> newVulnerabilities,
      int newFindingMaxAgeHours) {
    var lines = new ArrayList<String>();
    lines.add(ACTIVE_FINDINGS_HEADER);
    lines.add(totalsLine(activeFindings, Severity.CRITICAL));
    lines.add(totalsLine(activeFindings, Severity.HIGH));
    lines.add("");
    lines.add("*New in the last %d hours:*".formatted(newFindingMaxAgeHours));
    lines.addAll(vulnerabilityLines(newVulnerabilities));
    return String.join(LINE_BREAK, lines);
  }

  private static String totalsLine(List<Finding> activeFindings, Severity severity) {
    var matchingFindings =
        activeFindings.stream().filter(finding -> severity == finding.severity()).toList();
    var distinctVulnerabilities =
        matchingFindings.stream().map(InspectorDigestService::vulnerabilityId).distinct().count();
    return "%s: %d vulnerabilities affecting %d stacks"
        .formatted(severity, distinctVulnerabilities, countAffectedStacks(matchingFindings));
  }

  private static List<String> vulnerabilityLines(List<VulnerabilityAggregate> newVulnerabilities) {
    var lines =
        newVulnerabilities.stream()
            .limit(MAX_VULNERABILITY_LINES)
            .map(InspectorDigestService::vulnerabilityLine)
            .collect(toList());
    var truncatedCount = newVulnerabilities.size() - MAX_VULNERABILITY_LINES;
    if (truncatedCount > 0) {
      lines.add("...and %d more".formatted(truncatedCount));
    }
    return lines;
  }

  private static String vulnerabilityLine(VulnerabilityAggregate aggregate) {
    var fixPart =
        aggregate.fixedVersion().isEmpty() ? "" : " fix: %s".formatted(aggregate.fixedVersion());
    return "• %s %s `%s %s`%s (%d stacks)"
        .formatted(
            aggregate.vulnerabilityId(),
            aggregate.severity(),
            aggregate.packageName(),
            aggregate.packageVersion(),
            fixPart,
            aggregate.affectedStackCount());
  }

  private static AggregationKey aggregationKey(Finding finding) {
    var vulnerablePackage = firstVulnerablePackage(finding);
    return new AggregationKey(
        vulnerabilityId(finding),
        vulnerablePackage.map(VulnerablePackage::name).orElse(UNKNOWN),
        vulnerablePackage.map(VulnerablePackage::version).orElse(UNKNOWN));
  }

  private static VulnerabilityAggregate toAggregate(
      AggregationKey key, List<Finding> groupFindings) {
    return new VulnerabilityAggregate(
        key.vulnerabilityId(),
        key.packageName(),
        key.packageVersion(),
        highestSeverity(groupFindings),
        fixedVersion(groupFindings),
        countAffectedStacks(groupFindings));
  }

  /**
   * Inspector scores per resource, so one vulnerability can be HIGH on one function and CRITICAL
   * on another. The query only returns HIGH and CRITICAL findings, so the highest severity is
   * CRITICAL when any finding has it.
   */
  private static Severity highestSeverity(List<Finding> groupFindings) {
    var anyCritical =
        groupFindings.stream().anyMatch(finding -> Severity.CRITICAL == finding.severity());
    return anyCritical ? Severity.CRITICAL : groupFindings.getFirst().severity();
  }

  private static String fixedVersion(List<Finding> groupFindings) {
    return groupFindings.stream()
        .filter(finding -> FixAvailable.YES == finding.fixAvailable())
        .map(InspectorDigestService::firstVulnerablePackage)
        .flatMap(Optional::stream)
        .map(VulnerablePackage::fixedInVersion)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(NO_FIXED_VERSION);
  }

  private static long countAffectedStacks(List<Finding> findings) {
    return findings.stream()
        .flatMap(finding -> finding.resources().stream())
        .map(InspectorDigestService::stackIdentifier)
        .distinct()
        .count();
  }

  /**
   * Identifies a resource by its CloudFormation stack, since a stack maps one-to-one to a
   * microservice that is deployed and patched as a unit; counting individual Lambda functions would
   * inflate the numbers with every function of the same service. Falls back to the Lambda function
   * name (which still collapses Inspector's one-resource-per-scanned-version) and last the resource
   * id.
   */
  private static String stackIdentifier(Resource resource) {
    return Optional.ofNullable(resource.tags().get(STACK_NAME_TAG))
        .or(() -> lambdaFunctionName(resource))
        .orElseGet(resource::id);
  }

  private static Optional<String> lambdaFunctionName(Resource resource) {
    return Optional.ofNullable(resource.details())
        .map(ResourceDetails::awsLambdaFunction)
        .map(AwsLambdaFunctionDetails::functionName);
  }

  private static Optional<VulnerablePackage> firstVulnerablePackage(Finding finding) {
    return Optional.ofNullable(finding.packageVulnerabilityDetails())
        .map(PackageVulnerabilityDetails::vulnerablePackages)
        .flatMap(packages -> packages.stream().findFirst());
  }

  private static String vulnerabilityId(Finding finding) {
    return Optional.ofNullable(finding.packageVulnerabilityDetails())
        .map(PackageVulnerabilityDetails::vulnerabilityId)
        .orElse(UNKNOWN);
  }

  private static Comparator<VulnerabilityAggregate> displayOrder() {
    return Comparator.<VulnerabilityAggregate>comparingInt(
            aggregate -> Severity.CRITICAL == aggregate.severity() ? 0 : 1)
        .thenComparing(
            Comparator.comparingLong(VulnerabilityAggregate::affectedStackCount).reversed())
        .thenComparing(VulnerabilityAggregate::vulnerabilityId);
  }
}
