package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import den.tal.traffic.guard.kvs.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

        new WebcamStreamProcessor().processImages(strPayload, "data:image/png;base64,");
    }


}
