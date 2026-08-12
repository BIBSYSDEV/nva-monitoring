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
import java.util.Optional;
import no.sikt.nva.monitoring.model.ChatbotCustomNotification;
import no.sikt.nva.monitoring.model.DigestFinding;
import no.sikt.nva.monitoring.model.VulnerabilityAggregate;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.FilterCriteria;
import software.amazon.awssdk.services.inspector2.model.FindingStatus;
import software.amazon.awssdk.services.inspector2.model.FindingType;
import software.amazon.awssdk.services.inspector2.model.ListFindingsRequest;
import software.amazon.awssdk.services.inspector2.model.Severity;
import software.amazon.awssdk.services.inspector2.model.StringComparison;
import software.amazon.awssdk.services.inspector2.model.StringFilter;

/**
 * Builds the Inspector findings digest: fetches all active HIGH and CRITICAL package vulnerability
 * findings, and summarizes them as one Slack message with headline totals plus the new
 * vulnerabilities aggregated per vulnerability and package. Returns nothing when no vulnerability
 * was first observed within the max-age window. The digest frequency is owned by the EventBridge
 * schedule in template.yaml, together with the matching max-age window.
 */
public class InspectorDigestService {

  public static final int MAX_VULNERABILITY_LINES = 15;
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
    var newVulnerabilities = newVulnerabilities(activeFindings, newFindingMaxAgeHours);
    if (newVulnerabilities.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        createNotification(activeFindings, newVulnerabilities, newFindingMaxAgeHours));
  }

  private List<DigestFinding> fetchActiveFindings() {
    var findings = new ArrayList<DigestFinding>();
    String nextToken = null;
    do {
      var response = inspectorClient.listFindings(listFindingsRequest(nextToken));
      response.findings().stream().map(DigestFinding::fromSdk).forEach(findings::add);
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
      List<DigestFinding> activeFindings, int newFindingMaxAgeHours) {
    var cutoff = Instant.now(clock).minus(Duration.ofHours(newFindingMaxAgeHours));
    var groups =
        activeFindings.stream()
            .collect(groupingBy(DigestFinding::aggregationKey, LinkedHashMap::new, toList()));
    return groups.entrySet().stream()
        .filter(entry -> vulnerabilityFirstObservedAfter(entry.getValue(), cutoff))
        .map(entry -> VulnerabilityAggregate.create(entry.getKey(), entry.getValue()))
        .sorted(displayOrder())
        .toList();
  }

  private static boolean vulnerabilityFirstObservedAfter(
      List<DigestFinding> groupFindings, Instant cutoff) {
    return groupFindings.stream()
        .map(DigestFinding::firstObservedAt)
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
      List<DigestFinding> activeFindings,
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
      List<DigestFinding> activeFindings,
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

  private static String totalsLine(List<DigestFinding> activeFindings, Severity severity) {
    var matchingFindings =
        activeFindings.stream().filter(finding -> severity == finding.severity()).toList();
    var affectedStacks = DigestFinding.countDistinctStacks(matchingFindings);
    var distinctVulnerabilities =
        matchingFindings.stream().map(DigestFinding::vulnerabilityId).distinct().count();
    return "%s: %d vulnerabilities affecting %d stacks"
        .formatted(severity, distinctVulnerabilities, affectedStacks);
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

  private static Comparator<VulnerabilityAggregate> displayOrder() {
    return Comparator.<VulnerabilityAggregate>comparingInt(
            aggregate -> Severity.CRITICAL == aggregate.severity() ? 0 : 1)
        .thenComparing(
            Comparator.comparingLong(VulnerabilityAggregate::affectedStackCount).reversed())
        .thenComparing(VulnerabilityAggregate::vulnerabilityId);
  }
}
