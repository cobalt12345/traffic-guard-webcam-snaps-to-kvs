package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.*;
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

    @Test
    public void handleRequestTest() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        var url = getClass().getClassLoader().getResource(REQUEST_PAYLOAD);
        List<String> payload = Files.readAllLines(Paths.get(url.toURI()));
        payload.forEach(System.out::println);
        String strPayload = payload.stream().collect(Collectors.joining());

        new WebcamStreamProcessor().processImages(strPayload, "data:image/png;base64,", this::createMkvFile);
    }

    public void createMkvFile(ByteBuffer fragment) {
        try (var is = new ByteBufferBackedInputStream(fragment); var os = new FileOutputStream("frame.mkv")) {
            IOUtils.copy(is, os);
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }
}
