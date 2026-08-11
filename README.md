# NVA monitoring

Listens to stack being created, updated and deleted and updates cloudwatch dasboard.

## Inspector findings

The stack routes Amazon Inspector findings to Slack.
An EventBridge rule forwards active CRITICAL findings to the shared Slack SNS topic, reformatted as AWS Chatbot custom notifications.
Only CRITICAL findings get realtime notifications; their volume is low, and each Inspector finding is per CVE per Lambda function, so anything broader gets spammy.
Enabling Inspector itself is a manual account-level step, documented in the NVA-infrastructure README.

Accepted findings should be suppressed with `AWS::InspectorV2::Filter` resources in `template.yaml`, so suppressions are code reviewed.
There are none yet; add them as real suppressions come up.
