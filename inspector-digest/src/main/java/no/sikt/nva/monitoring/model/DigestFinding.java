package no.sikt.nva.monitoring.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.inspector2.model.AwsLambdaFunctionDetails;
import software.amazon.awssdk.services.inspector2.model.Finding;
import software.amazon.awssdk.services.inspector2.model.FixAvailable;
import software.amazon.awssdk.services.inspector2.model.PackageVulnerabilityDetails;
import software.amazon.awssdk.services.inspector2.model.Resource;
import software.amazon.awssdk.services.inspector2.model.ResourceDetails;
import software.amazon.awssdk.services.inspector2.model.Severity;
import software.amazon.awssdk.services.inspector2.model.VulnerablePackage;

/**
 * One Inspector package vulnerability finding reduced to the fields the digest needs, with all SDK
 * null-handling confined to the factory: unknown identifiers become "unknown", a missing fixed
 * version becomes the empty string, and a missing firstObservedAt becomes Instant.EPOCH so findings
 * of unknown age never count as new.
 */
public record DigestFinding(
    String vulnerabilityId,
    String packageName,
    String packageVersion,
    Severity severity,
    String fixedVersion,
    Instant firstObservedAt,
    List<String> affectedStacks) {

  public static final String NO_FIXED_VERSION = "";
  private static final String UNKNOWN = "unknown";
  private static final String STACK_NAME_TAG = "aws:cloudformation:stack-name";

  public static DigestFinding fromSdk(Finding finding) {
    var vulnerablePackage = firstVulnerablePackage(finding);
    return new DigestFinding(
        vulnerabilityId(finding),
        vulnerablePackage.map(VulnerablePackage::name).orElse(UNKNOWN),
        vulnerablePackage.map(VulnerablePackage::version).orElse(UNKNOWN),
        finding.severity(),
        fixedVersion(finding),
        Optional.ofNullable(finding.firstObservedAt()).orElse(Instant.EPOCH),
        finding.resources().stream().map(DigestFinding::stackIdentifier).toList());
  }

  public AggregationKey aggregationKey() {
    return new AggregationKey(vulnerabilityId, packageName, packageVersion);
  }

  public static long countDistinctStacks(List<DigestFinding> findings) {
    return findings.stream()
        .flatMap(finding -> finding.affectedStacks().stream())
        .distinct()
        .count();
  }

  private static String vulnerabilityId(Finding finding) {
    return Optional.ofNullable(finding.packageVulnerabilityDetails())
        .map(PackageVulnerabilityDetails::vulnerabilityId)
        .orElse(UNKNOWN);
  }

  private static String fixedVersion(Finding finding) {
    return FixAvailable.YES == finding.fixAvailable()
        ? firstVulnerablePackage(finding)
            .map(VulnerablePackage::fixedInVersion)
            .orElse(NO_FIXED_VERSION)
        : NO_FIXED_VERSION;
  }

  private static Optional<VulnerablePackage> firstVulnerablePackage(Finding finding) {
    return Optional.ofNullable(finding.packageVulnerabilityDetails())
        .map(PackageVulnerabilityDetails::vulnerablePackages)
        .flatMap(packages -> packages.stream().findFirst());
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
}
