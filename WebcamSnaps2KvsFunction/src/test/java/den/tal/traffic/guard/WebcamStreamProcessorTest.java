package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.util.Base64;
import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.json.BodyPayload;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.transcode.*;
import org.jcodec.api.transcode.filters.ScaleFilter;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.Tuple;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


@SetEnvironmentVariable(key = "Path2FFmpeg",
        value = "D:\\Program Files\\ffmpeg-2021-05-19-git-2261cc6d8a-full_build\\bin\\ffmpeg.exe")

@SetEnvironmentVariable(key = "videoWidth", value = "640")
@SetEnvironmentVariable(key = "videoHeight", value = "480")
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

    @Disabled
//    @Test
    public void handleRequestTest() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        var url = getClass().getClassLoader().getResource(REQUEST_PAYLOAD);
        List<String> payload = Files.readAllLines(Paths.get(url.toURI()));
        payload.forEach(System.out::println);
        String strPayload = String.join("", payload);
        WebcamStreamProcessor processor = new WebcamStreamProcessor();
        Path imagesFolder = processor.convertImages(strPayload);
        log.debug("Images temp folder: {}", imagesFolder.toAbsolutePath());
        Path mkvFile = processor.convertImagesToMkv(imagesFolder);
        log.debug("Temp Mkv file: {}", mkvFile.toAbsolutePath());
    }

    @Disabled
//    @Test
    public void detectFileTypeTest() throws Exception {
        WebcamStreamProcessor processor = new WebcamStreamProcessor();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        var url = getClass().getClassLoader().getResource(REQUEST_PAYLOAD);
        List<String> payload = Files.readAllLines(Paths.get(url.toURI()));
        String strPayload = String.join("", payload);
        BodyPayload bodyPayload = new GsonBuilder().create().fromJson(strPayload, BodyPayload.class);
        Arrays.stream(bodyPayload.getFrames()).forEach(
                image64base -> {
                    String normalizedImage64base = processor.normalize(image64base);
                    byte[] image = Base64.decode(normalizedImage64base);
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(image);
                         BufferedInputStream bis = new BufferedInputStream(bais)) {
                        FileType fileType = FileTypeDetector.detectFileType(bis);
                        log.debug("Detected file type: {}", fileType);
                        bais.reset();
                        BufferedImage bufferedImage = ImageIO.read(bais);

                    } catch(IOException ioex) {
                        throw new RuntimeException(ioex);
                    }
                }
        );
    }

    @Disabled
//    @Test
    public void supportedFormatNamesTest() {
        for (String formatName : ImageIO.getWriterFormatNames()) {
            System.out.println("formatName = " + formatName);
        }
    }

    @Disabled
//    @Test
    public void fileNamePatternTest() {
        final String pattern = "img%03d.jpg";
        log.debug("Image file name: {}", String.format(pattern, 0));
    }
}
