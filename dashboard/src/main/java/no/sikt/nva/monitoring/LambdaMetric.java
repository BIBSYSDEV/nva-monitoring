package no.sikt.nva.monitoring;

public record LambdaMetric(String stat, String region, Boolean visible) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String stat;
    private String region;
    private Boolean visible;

    private Builder() {}

    public Builder withStat(String stat) {
      this.stat = stat;
      return this;
    }

    public Builder withRegion(String region) {
      this.region = region;
      return this;
    }

    public Builder withVisible(Boolean visible) {
      this.visible = visible;
      return this;
    }

    public LambdaMetric build() {
      return new LambdaMetric(stat, region, visible);
    }
  }
}
