package no.sikt.nva.monitoring;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import java.lang.ProcessHandle.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateDashboardHandler implements RequestHandler<CloudFormationCustomResourceEvent, Void> {
    private static final Logger logger = LoggerFactory.getLogger(UpdateDashboardHandler.class);


    @Override
    public Void handleRequest(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
        context.getLogger().log("Fudge", LogLevel.INFO);
        logger.info("Hello world");
        logger.warn("Try again");
        logger.error("ERRROR");
        System.out.println("testing");
        //Future plans:
        //List all lambdas in the account
        //List all alarms in the account
        //Create cloudwatch widgets for lambdas
        //Create cloudwatch widgets for alarm
        //Attach widgets to cloudwatch dashboard.
        //Update sharing policy? and share dashboard
        return null;
    }
}
