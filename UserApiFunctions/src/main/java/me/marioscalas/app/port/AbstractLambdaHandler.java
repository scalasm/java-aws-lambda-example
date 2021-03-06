package me.marioscalas.app.port;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import me.marioscalas.app.adapter.CognitoConfig;
import me.marioscalas.app.adapter.CognitoUserService;
import me.marioscalas.app.core.service.UserService;
import software.amazon.awssdk.awscore.exception.AwsServiceException;



public abstract class AbstractLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    // Static Initialization here 

    /**
     * Default GSON serializer - null attributes won't get serialized.
     */
    protected static final Gson GSON = new Gson();

    /**
     * Shared user service instance
     */
    protected final UserService userService;

    protected AbstractLambdaHandler(UserService userService) {
        this.userService = userService;
    }

    protected AbstractLambdaHandler() {
        this.userService = getUserService();
    }

    @Override
    public final APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        final LambdaLogger logger = context.getLogger();

        logger.log("Using lambda function v" + context.getFunctionVersion());

        final Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        final APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);
        try {
            onAPIGatewayProxyRequestEvent(input, response, context);
        } catch (AwsServiceException e) {
            logger.log("AWS SDK Error: " + e.getMessage());

            response.setStatusCode(e.awsErrorDetails().sdkHttpResponse().statusCode());
            response.setBody(
                GSON.toJson(
                    new ErrorResponse(
                        e.awsErrorDetails().errorMessage(),
                        ErrorResponse.getStackTraceAsString(e)
                    )
                )
            );
        } catch (Exception e) {
            logger.log("Whops!: " + e.getMessage());

            response.setStatusCode(500);
            response.setBody(
                GSON.toJson(
                    ErrorResponse.fromException(e)
                )
            );
        }

        return response;
    }

    protected abstract void onAPIGatewayProxyRequestEvent(APIGatewayProxyRequestEvent input, APIGatewayProxyResponseEvent response, Context context);

    private static UserService getUserService() {
        final String appClientId = Utils.getenv("COGNITO_APP_CLIENT_ID");
        final String appClientSecret = Utils.getenv("COGNITO_APP_CLIENT_SECRET");
        final String userPoolId = Utils.getenv("COGNITO_USER_POOL_ID");
        final String region = System.getenv("AWS_REGION");

        final CognitoConfig config = new CognitoConfig(
            appClientId, appClientSecret, userPoolId, region
        );
        
        return new CognitoUserService(config);
    }
}
