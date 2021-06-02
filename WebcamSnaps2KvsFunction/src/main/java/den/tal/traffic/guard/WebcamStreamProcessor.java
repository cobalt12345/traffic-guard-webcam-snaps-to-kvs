package den.tal.traffic.guard;

import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.kinesisvideo.PutMediaAckResponseHandler;
import com.amazonaws.services.kinesisvideo.model.AckEvent;
import com.amazonaws.services.kinesisvideo.model.FragmentTimecodeType;
import com.amazonaws.services.kinesisvideo.model.PutMediaRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.json.BodyPayload;
import den.tal.traffic.guard.kvs.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
{
    private static final String PNG_URI_PREFIX = "data:image/png;base64,";
    private static final String JPG_URI_PREFIX = "data:image/jpg;base64,";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private H264Encoder encoder = H264Encoder.createH264Encoder();
//    {
//        Utils.printOutDirectoryRecursive(Paths.get("/opt"));
//    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
//            Utils.logEnvironment(request, context, gson);
            Path tmpFolder = convertImages(request.getBody());
            Path mkvFile = convertImagesToMkv(tmpFolder);
            log.debug("Created MKV container file: {}", mkvFile);
            CountDownLatch latch = new CountDownLatch(1);
            try (InputStream is = new FileInputStream(mkvFile.toFile());
                 AmazonKinesisVideoPutMedia putMediaClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                         Utils.getKvsName())) {
                log.debug("Send MKV as a fragment to KVS...");
                putMediaClient.putMedia(new PutMediaRequest().withStreamName(Utils.getKvsName())
                        .withFragmentTimecodeType(FragmentTimecodeType.RELATIVE)
                        .withProducerStartTimestamp(new Date())
                        .withPayload(is), new PutMediaAckResponseHandler() {
                    @Override
                    public void onAckEvent(AckEvent event) {
                        log.debug("Fragment ack. {}", event);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("Fragment send error.", t);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        log.debug("Fragment sent.");
                        latch.countDown();
                    }
                });
                latch.await();
            }

            Files.list(tmpFolder).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException ioex) {
                    log.warn(String.format("Could not remove file '%s' after processing.", f.toString()), ioex);
                }
            });

            Files.delete(tmpFolder);
            log.debug("Cleaned up...");
        } catch (IOException | InterruptedException ioex) {
            log.error("Request body conversion error.", ioex);

            throw new RuntimeException(ioex);
        }

        return Utils.getResponse(200, "OK");
    }

    Path convertImagesToMkv(Path folder) throws IOException {
        long numOfPngFiles = Files.list(folder).count();
        String ffmpegPath = Utils.getPath2ffmpeg();
        log.debug("FFmpeg is installed in: {}", ffmpegPath);
        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFmpegBuilder builder = new FFmpegBuilder();
        Path mkvFile = folder.resolve("fragment.mkv");
        builder.addExtraArgs("-r", Long.toString(numOfPngFiles))
                .setInput(folder.resolve("img%03d.png").toAbsolutePath().toString())
                .addOutput(new FFmpegOutputBuilder().setVideoFrameRate(numOfPngFiles).setVideoCodec("libx264")
                        .setVideoPixelFormat("yuv420p").setFormat("matroska")
                            .setVideoResolution(Utils.getWidth(), Utils.getHeight())
                                .setFilename(mkvFile.toAbsolutePath().toString()));

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);
        log.debug("Start FFmpeg job...");
        executor.createJob(builder).run();
        log.debug("FFmpeg job finished.");
        if (Files.notExists(mkvFile)) {
            throw new IOException("MKV file not created.");
        } else {

            return mkvFile;
        }
    }

    Path convertImages(String jsonBody) throws IOException {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            int bufferSize = 0;
            Rational rational = Rational.R(payload.getFrames().length, 1);
            Path tmpDir = Files.createTempDirectory("imgs");
            for (int i = 0; i < payload.getFrames().length; ++i) {
                log.debug("Process image. Batch ordinal num: {}.", i);
                final String image64base = payload.getFrames()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base));
                Picture picture = AWTUtil.fromBufferedImage(bufferedImage, encoder.getSupportedColorSpaces()[0]);
                File pngFileName = new File(String.format("img%03d.png", i));
                Path pngFile = Files.createFile(Paths.get(tmpDir.toAbsolutePath().toString(), pngFileName.getName()));
                log.debug("Save PNG {}", pngFile.toAbsolutePath());
                AWTUtil.savePicture(picture, "PNG", pngFile.toFile());
            }

            return tmpDir;

        } else {
            log.debug("Method body is empty. No images for processing.");

            return null;
        }
    }


    String normalize(String image64base) {
        if (image64base.regionMatches(true, 0, JPG_URI_PREFIX, 0, JPG_URI_PREFIX.length())) {
            log.debug("Jpg image");

            return image64base.substring(JPG_URI_PREFIX.length());

        } else if (image64base.regionMatches(true, 0, PNG_URI_PREFIX, 0, PNG_URI_PREFIX.length())) {
            log.debug("Png image");

            return image64base.substring(PNG_URI_PREFIX.length());

        } else {
            log.debug("Uri contains no type prefix");

            return image64base;
        }
    }

    BufferedImage convertToImage(String image64base) {
        byte[] image = Base64.decode(image64base);
        try (ByteArrayInputStream is = new ByteArrayInputStream(image)) {
            BufferedImage bufferedImage = ImageIO.read(is);

            return bufferedImage;

        } catch (IOException ioex) {
            log.error("Could not decode base64 image.", ioex);

            return null;
        }
    }
}