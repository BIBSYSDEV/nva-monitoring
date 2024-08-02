package no.sikt.nva.monitoring.model;

public record LogProperties(String region, String title, String query, String view) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String region;
        private String title;
        private String query;
        private String view;

        private Builder() {
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder withView(String view) {
            this.view = view;
            return this;
        }

        public LogProperties build() {
            return new LogProperties(region, title, query, view);
        }
    }
}