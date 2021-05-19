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
import org.jcodec.api.SequenceEncoder;
import org.jcodec.codecs.png.PNGDecoder;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.IOUtils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Instant;
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
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            File tempFile = convertImagesToFile(request.getBody(), Utils.getImageFormatPrefix());
            if (null != tempFile) {
                sendFile2Kvs(tempFile);
            } else {
                log.warn("Temp file is null!");
            }
        } catch (IOException ioex) {
            log.error("Error writing images to temp file.", ioex);

            throw new RuntimeException(ioex);
        }

        return Utils.getResponse(200, "OK");
    }

    void sendFile2Kvs(File mkvFile) {
        Date startRecording = Date.from(Instant.now());
        AmazonKinesisVideoPutMedia kinesisVideoPutMediaClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                Utils.getKvsName());

        try (InputStream is = new FileInputStream(mkvFile)) {
            CountDownLatch latch = new CountDownLatch(1);
            kinesisVideoPutMediaClient.putMedia(new PutMediaRequest().withStreamName(Utils.getKvsName())
                            .withFragmentTimecodeType(FragmentTimecodeType.RELATIVE)
                            .withPayload(is).withProducerStartTimestamp(Date.from(Instant.now())),
                    new PutMediaAckResponseHandler(){

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

                        @Override
                        public void onAckEvent(AckEvent event) {
                            log.debug("Fragment ack. {}", event);
                        }
                    });

            latch.await();
            is.close();
            kinesisVideoPutMediaClient.close();
            log.debug("Delete temp file: {}", mkvFile.toPath());
            Files.delete(mkvFile.toPath());
        } catch (InterruptedException | IOException  ex) {
            log.error("File can not be sent to KVS", ex);
        }
    }

    File convertImagesToFile(String jsonBody, String imageFormat) throws IOException {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            final File tmpMkvFile;
            final FileChannelWrapper out;
            final SequenceEncoder sequenceEncoder;
            tmpMkvFile = Files.createTempFile(Utils.getMkvsFolder(), ".mkv").toFile();
            log.debug("Temp file path: {}", tmpMkvFile.getAbsolutePath());
            out = NIOUtils.writableChannel(tmpMkvFile);
            sequenceEncoder = new SequenceEncoder(out, Rational.R(payload.getFrames().length, 1), Format.MKV,
                    Codec.H264, null);

            for (int i = 0; i < payload.getFrames().length; ++i) {
                log.debug("Process image. Batch ordinal num: {}.", i);

                final String image64base = payload.getFrames()[i];
                BufferedImage bufferedImage = Utils.convertToImage(normalize(image64base, imageFormat));
                Picture picture = AWTUtil.fromBufferedImage(bufferedImage, ColorSpace.RGB);
                sequenceEncoder.encodeNativeFrame(picture);
            }
            sequenceEncoder.finish();
            out.close();

            return tmpMkvFile;

        } else {
            log.debug("Method body is empty. No images for processing.");

            return null;
        }
    }

    String normalize(String image64base, String imageFormat) {
        log.debug("Prefix: {} Image64base: {}", imageFormat, image64base.substring(0, imageFormat.length() + 5)
                .concat("..."));

        return image64base.substring(imageFormat.length());
    }


}