package no.sikt.nva.monitoring.model.factory;

import no.sikt.nva.monitoring.model.CloudWatchWidget;
import no.sikt.nva.monitoring.model.TextProperties;

public final class DocumentationLinksWidget {

    public static final String TEXT = "text";
    public static final String INSTRUCTIONS_URL =
        "https://sikt.atlassian.net/wiki/spaces/NVAP/pages/4021749297/Backend+support+rotation";
    public static final String INCIDENT_RESPONSE_URL =
        "https://sikt.atlassian.net/wiki/spaces/NVAP/pages/4074700838/Incident+response+routine";
    public static final String REDRIVE_DLQ_URL =
        "https://sikt.atlassian.net/wiki/spaces/NVAP/pages/4122181684/Processing+of+messages+on+DLQ";
    public static final String MARKDOWN =
        "[Instructions](%s) | [Incident response](%s) | [Redrive DLQ](%s)"
            .formatted(INSTRUCTIONS_URL, INCIDENT_RESPONSE_URL, REDRIVE_DLQ_URL);
    public static final int HEIGHT = 1;
    public static final int WIDTH = 24;
    public static final int X_COORDINATE = 0;
    public static final int Y_COORDINATE = 0;

    private DocumentationLinksWidget() {
    }

    public static CloudWatchWidget<TextProperties> create() {
        return new CloudWatchWidget<>(TEXT, new TextProperties(MARKDOWN), HEIGHT, WIDTH, X_COORDINATE, Y_COORDINATE);
    }
}
