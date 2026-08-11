# NVA monitoring

Listens to stack being created, updated and deleted and updates cloudwatch dasboard.

## Inspector findings

The stack routes Amazon Inspector findings to Slack.
An EventBridge rule forwards active CRITICAL findings to the shared Slack SNS topic, reformatted as AWS Chatbot custom notifications.
Only CRITICAL findings get realtime notifications; their volume is low, and each Inspector finding is per CVE per Lambda function, so anything broader gets spammy.
HIGH findings are instead covered by the daily digest.

The `inspector-digest` Lambda runs every day at 06:00 UTC.
It lists all active HIGH and CRITICAL package vulnerability findings through the Inspector API.
If any findings were first observed within the last 24 hours (configurable via `NEW_FINDING_MAX_AGE_HOURS`), it posts one Slack message with headline totals per severity and the new findings aggregated per CVE and package, including the fixed version when one is available.
When nothing new has appeared, it posts nothing.
Enabling Inspector itself is a manual account-level step, documented in the NVA-infrastructure README.

Accepted findings should be suppressed with `AWS::InspectorV2::Filter` resources in `template.yaml`, so suppressions are code reviewed.
There are none yet; add them as real suppressions come up.
