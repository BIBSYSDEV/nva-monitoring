package no.sikt.nva.monitoring;

import java.util.List;

public record LambdaProperties(List<List<Object>> metrics,
                               String title,
                               String view,
                               String region,
                               String stat,
                               int period,
                               boolean liveData,
                               boolean trend,
                               boolean singleValueFullPrecision,
                               boolean stacked,
                               boolean sparkline) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<List<Object>> metrics;
        private String title;
        private String view;
        private String region;
        private String stat;
        private int period;
        private boolean liveData;
        private boolean trend;
        private boolean singleValueFullPrecision;
        private boolean stacked;
        private boolean sparkline;

        private Builder() {
        }

        public Builder withMetrics(List<List<Object>> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withView(String view) {
            this.view = view;
            return this;
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withStat(String stat) {
            this.stat = stat;
            return this;
        }

        public Builder withPeriod(int period) {
            this.period = period;
            return this;
        }

        public Builder withLiveData(boolean liveData) {
            this.liveData = liveData;
            return this;
        }

        public Builder withTrend(boolean trend) {
            this.trend = trend;
            return this;
        }

        public Builder withSingleValueFullPrecision(boolean singleValueFullPrecision) {
            this.singleValueFullPrecision = singleValueFullPrecision;
            return this;
        }

        public Builder withStacked(boolean stacked) {
            this.stacked = stacked;
            return this;
        }

        public Builder withSparkline(boolean sparkline) {
            this.sparkline = sparkline;
            return this;
        }

        public LambdaProperties build() {
            return new LambdaProperties(metrics, title, view, region, stat, period, trend,liveData,
                                        singleValueFullPrecision, stacked, sparkline);
        }
    }
}
