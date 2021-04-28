package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
{
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setIsBase64Encoded(false);
        response.setStatusCode(200);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE");
        headers.put("Access-Control-Max-Age", "3600");
        headers.put("Access-Control-Allow-Headers", "x-requested-with, content-type");
        response.setHeaders(headers);
        response.setBody("OK");

        return response;
    }
}
