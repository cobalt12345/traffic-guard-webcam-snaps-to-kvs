package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.transcode.*;
import org.jcodec.api.transcode.filters.ScaleFilter;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.Tuple;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class WebcamStreamProcessorTest {

    private static final String REQUEST_PAYLOAD = "request_payload.json";

//    @Test
    public void webMovieAndMKVStreamingMuxerTest() throws IOException {
        Transcoder.TranscoderBuilder builder = Transcoder.newTranscoder();
        String sourceName = "C:\\Users\\User\\AppData\\Local\\Temp\\img%d.png";

        String destName = Files.createTempFile("kvs", ".mkv").toAbsolutePath().toString();
        Source source = new SourceImpl(sourceName, Format.IMG, Tuple.triple(0, 0, Codec.PNG), null);
        source.setOption(Options.INTERLACED, false);
        builder.addSource(source);
        builder.setSeekFrames(0, 0).setMaxFrames(0, 25);

        Sink sink = new SinkImpl(destName, Format.MKV, Codec.H264, null);
        builder.addSink(sink);
        builder.addFilter(0, new ScaleFilter(640, 480));
        builder.setVideoMapping(0, 0, true);
        Transcoder transcoder = builder.create();
        transcoder.transcode();


    }

    @Test
    public void handleRequestTest() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        var url = getClass().getClassLoader().getResource(REQUEST_PAYLOAD);
        List<String> payload = Files.readAllLines(Paths.get(url.toURI()));
        payload.forEach(System.out::println);
        String strPayload = payload.stream().collect(Collectors.joining());

        ByteBufferSeekableByteChannel outChannel = new WebcamStreamProcessor().convertImages(strPayload,
                "data:image/png;base64,", true);

        Path mkvFile = Files.createTempFile("", ".mkv");
        log.debug("Temp Mkv file: {}", mkvFile.toAbsolutePath());
        try (outChannel; FileChannelWrapper fileChannel = NIOUtils.writableChannel(mkvFile.toFile())) {
            fileChannel.write(outChannel.getContents());
        }
    }

    public void createMkvFile(ByteBuffer fragment) {
        try (var is = new ByteBufferBackedInputStream(fragment); var os = new FileOutputStream("frame.mkv")) {
            IOUtils.copy(is, os);
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }
}
