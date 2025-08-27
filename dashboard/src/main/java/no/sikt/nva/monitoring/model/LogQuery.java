package no.sikt.nva.monitoring.model;

import java.util.List;
import java.util.stream.Collectors;

public record LogQuery(List<String> logGroups, String filter, String fields, String stats, String sort, String limit) {

    public static final String DELIMITER = " | ";

    public static Builder builder() {
        return new Builder();
    }

    public String constructQuery() {
        var logGroups = logGroups().stream()
                                           .map(LogQuery::toSource)
                                           .collect(Collectors.joining(DELIMITER));
        return String.join(DELIMITER, logGroups, filter, fields, stats, sort, limit);
    }

    private static String toSource(String name) {
        return "SOURCE '" + name + "'";
    }

    public static final class Builder {

        private List<String> logGroups;
        private String filter;
        private String fields;
        private String sort;
        private String limit;
        private String stats;

        private Builder() {
        }

        public Builder withLogGroups(List<String> logGroups) {
            this.logGroups = logGroups;
            return this;
        }

        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder withFields(String fields) {
            this.fields = fields;
            return this;
        }

        public Builder withSort(String sort) {
            this.sort = sort;
            return this;
        }

        public Builder withLimit(String limit) {
            this.limit = limit;
            return this;
        }

        public Builder withStats(String stats) {
            this.stats = stats;
            return this;
        }

        public LogQuery build() {
            return new LogQuery(logGroups, filter, fields, stats, sort, limit);
        }
    }
}
