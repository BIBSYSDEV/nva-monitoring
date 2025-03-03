package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse.PolicyDocument;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse.Statement;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListRolesRequest;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.Role;

public class DashboardPolicyDocument {
    private static final String ALLOW_EFFECT = "Allow";
    private static final String POLICY_VERSION = "2012-10-17";
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardPolicyDocument.class);
    private static final String CW_SHARING_PREFIX = "CWDBSharing-ReadOnlyAccess-";

    private DashboardPolicyDocument() {
    }

    private static PolicyDocument createPolicyDocument() {
        var policies = new ArrayList<Statement>();

        Statement statement1 = new Statement();
        statement1.setEffect(ALLOW_EFFECT);
        statement1.setAction("ec2:DescribeTags,cloudwatch:GetMetricData");
        statement1.setResource(List.of("*"));
        policies.add(statement1);

        Statement statement2 = new Statement();
        statement2.setEffect(ALLOW_EFFECT);
        statement2.setAction("cloudwatch:GetInsightRuleReport,cloudwatch:DescribeAlarms,cloudwatch:GetDashboard");
        statement2.setResource(List.of(
            "arn:aws:cloudwatch::*:dashboard/*",
            "arn:aws:cloudwatch:eu-west-1:*:alarm:*"
        ));
        policies.add(statement2);

        Statement statement3 = new Statement();
        statement3.setEffect(ALLOW_EFFECT);
        statement3.setAction("logs:DescribeLogStreams,logs:GetLogEvents,logs:StartQuery,logs:GetLogRecord");
        statement3.setResource(List.of(
            "arn:aws:logs:eu-west-1:*:log-group:*"
        ));
        policies.add(statement3);

        PolicyDocument policyDocument = new PolicyDocument();
        policyDocument.setVersion(POLICY_VERSION);
        policyDocument.setStatement(policies);

        return policyDocument;
    }

    private static List<Role> listAllRoles(IamClient iamClient) {
        List<Role> roles = new ArrayList<>();
        String marker = null;

        do {
            ListRolesRequest request = ListRolesRequest.builder()
                                           .marker(marker)
                                           .build();

            ListRolesResponse response = iamClient.listRoles(request);
            roles.addAll(response.roles());
            marker = response.marker();
        } while (marker != null);

        return roles;
    }

    public static void updateRolePolicy(IamClient iamClient) {
        var cwSharingRole = listAllRoles(iamClient).stream()
                                .filter(role -> role.roleName().startsWith(CW_SHARING_PREFIX))
                                .findFirst();

        if (cwSharingRole.isEmpty()) {
            LOGGER.error("No role found for updating policy");
            return;
        }

        var policyJson = asString(createPolicyDocument());

        iamClient.putRolePolicy(PutRolePolicyRequest.builder()
                                    .roleName(cwSharingRole.get().roleName())
                                    .policyDocument(policyJson)
                                    .build());
    }

    @JacocoGenerated
    private static String asString(PolicyDocument policyDocument) {
        try {
            return JsonUtils.dtoObjectMapper.writeValueAsString(policyDocument);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
