# NVA monitoring

Listens to stack being created, updated and deleted and updates cloudwatch dasboard.

## Inspector findings

The stack routes Amazon Inspector findings to Slack.
An EventBridge rule forwards active HIGH and CRITICAL findings to the shared Slack SNS topic, reformatted as AWS Chatbot custom notifications.
Enabling Inspector itself is a manual account-level step, documented in the NVA-infrastructure README.

Accepted findings should be suppressed with `AWS::InspectorV2::Filter` resources in `template.yaml`, so suppressions are code reviewed.
There are none yet; add them as real suppressions come up.
