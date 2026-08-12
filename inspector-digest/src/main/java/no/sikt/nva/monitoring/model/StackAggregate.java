package no.sikt.nva.monitoring.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.inspector2.model.Severity;

/**
 * Active vulnerability counts for one CloudFormation stack, answering which services need patching.
 * A vulnerability affecting several functions in the same stack counts once, at its highest
 * severity within the stack.
 */
public record StackAggregate(String stackName, long criticalCount, long highCount) {

  public static List<StackAggregate> createAll(List<DigestFinding> activeFindings) {
    var severityByVulnerabilityByStack = new HashMap<String, Map<String, Severity>>();
    for (var finding : activeFindings) {
      for (var stack : finding.affectedStacks()) {
        severityByVulnerabilityByStack
            .computeIfAbsent(stack, key -> new HashMap<>())
            .merge(finding.vulnerabilityId(), finding.severity(), DigestFinding::highestSeverity);
      }
    }
    return severityByVulnerabilityByStack.entrySet().stream()
        .map(entry -> create(entry.getKey(), entry.getValue().values()))
        .toList();
  }

  private static StackAggregate create(String stackName, Collection<Severity> severities) {
    var criticalCount =
        severities.stream().filter(severity -> Severity.CRITICAL == severity).count();
    return new StackAggregate(stackName, criticalCount, severities.size() - criticalCount);
  }
}
