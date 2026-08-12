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
import software.amazon.awssdk.services.inspector2.model.FilterCriteria;
import software.amazon.awssdk.services.inspector2.model.Finding;
import software.amazon.awssdk.services.inspector2.model.FindingStatus;
import software.amazon.awssdk.services.inspector2.model.FindingType;
import software.amazon.awssdk.services.inspector2.model.FixAvailable;
import software.amazon.awssdk.services.inspector2.model.ListFindingsRequest;
import software.amazon.awssdk.services.inspector2.model.PackageVulnerabilityDetails;
import software.amazon.awssdk.services.inspector2.model.Resource;
import software.amazon.awssdk.services.inspector2.model.Severity;
import software.amazon.awssdk.services.inspector2.model.StringComparison;
import software.amazon.awssdk.services.inspector2.model.StringFilter;
import software.amazon.awssdk.services.inspector2.model.VulnerablePackage;

/**
 * Builds the Inspector findings digest: fetches all active HIGH and CRITICAL package vulnerability
 * findings, and summarizes them as one Slack message with headline totals plus the new findings
 * aggregated per vulnerability and package. Returns nothing when no finding was first observed
 * within the max-age window. The digest frequency is owned by the EventBridge schedule in
 * template.yaml, together with the matching max-age window.
 */
public class InspectorDigestService {

  public static final int MAX_VULNERABILITY_LINES = 15;
  private static final String UNKNOWN = "unknown";
  private static final String NO_FIXED_VERSION = "";
  private static final String ACTIVE_FINDINGS_HEADER =
      "*Active HIGH and CRITICAL dependency vulnerabilities:*";
  private static final String LINE_BREAK = "\n";

  private final Inspector2Client inspectorClient;
  private final Clock clock;

  public InspectorDigestService(Inspector2Client inspectorClient, Clock clock) {
    this.inspectorClient = inspectorClient;
    this.clock = clock;
  }

  public Optional<ChatbotCustomNotification> createDigest(int newFindingMaxAgeHours) {
    var activeFindings = fetchActiveFindings();
    var newFindings = findingsObservedAfterCutoff(activeFindings, newFindingMaxAgeHours);
    if (newFindings.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(createNotification(activeFindings, newFindings, newFindingMaxAgeHours));
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

  private List<Finding> findingsObservedAfterCutoff(
      List<Finding> findings, int newFindingMaxAgeHours) {
    var cutoff = Instant.now(clock).minus(Duration.ofHours(newFindingMaxAgeHours));
    return findings.stream()
        .filter(finding -> nonNull(finding.firstObservedAt()))
        .filter(finding -> finding.firstObservedAt().isAfter(cutoff))
        .toList();
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
      List<Finding> activeFindings, List<Finding> newFindings, int newFindingMaxAgeHours) {
    var newVulnerabilities = aggregateByVulnerabilityAndPackage(newFindings);
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
    return "%s: %d vulnerabilities affecting %d Lambda functions"
        .formatted(severity, distinctVulnerabilities, countAffectedFunctions(matchingFindings));
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
    return "• %s %s `%s %s`%s (%d functions)"
        .formatted(
            aggregate.vulnerabilityId(),
            aggregate.severity(),
            aggregate.packageName(),
            aggregate.packageVersion(),
            fixPart,
            aggregate.affectedFunctionCount());
  }

  private static List<VulnerabilityAggregate> aggregateByVulnerabilityAndPackage(
      List<Finding> findings) {
    var groups =
        findings.stream()
            .collect(
                groupingBy(InspectorDigestService::aggregationKey, LinkedHashMap::new, toList()));
    return groups.entrySet().stream()
        .map(entry -> toAggregate(entry.getKey(), entry.getValue()))
        .sorted(displayOrder())
        .toList();
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
        groupFindings.getFirst().severity(),
        fixedVersion(groupFindings),
        countAffectedFunctions(groupFindings));
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

  private static long countAffectedFunctions(List<Finding> findings) {
    return findings.stream()
        .flatMap(finding -> finding.resources().stream())
        .map(Resource::id)
        .distinct()
        .count();
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
            Comparator.comparingLong(VulnerabilityAggregate::affectedFunctionCount).reversed())
        .thenComparing(VulnerabilityAggregate::vulnerabilityId);
  }
}
