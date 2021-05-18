package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class WebcamStreamProcessorTest {

    private static final String REQUEST_PAYLOAD = "request_payload.json";

//    @Before
    public void initEnv() {
        System.setProperty("URLDataFormat", "data:image/png;base64,");
        System.setProperty("KVSStreamName", "traffic-guard");
        System.setProperty("KVSRegion", "eu-central-1");
        System.setProperty("GeneratedMkvsFolder", "mkv");

        log.debug(System.getenv().toString());
    }

    public static void main(String[] args) throws Exception {
        new WebcamStreamProcessorTest().handleRequestTest();
    }

//    @Ignore
//    @Test
    public void handleRequestTest() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        URL url = getClass().getClassLoader().getResource(REQUEST_PAYLOAD);
        List<String> payload = Files.readAllLines(Paths.get(url.toURI()));
        payload.forEach(System.out::println);
        String strPayload = payload.stream().collect(Collectors.joining());

        new WebcamStreamProcessor().convertImagesToFile(strPayload, "data:image/png;base64,");
    }


}
