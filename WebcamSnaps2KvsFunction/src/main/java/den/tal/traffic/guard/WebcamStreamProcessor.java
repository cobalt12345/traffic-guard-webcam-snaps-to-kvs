package den.tal.traffic.guard;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.json.BodyPayload;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
{
    private static final String URL_DATA_FORMAT_VAR = "URLDataFormat";
    private static final String KVS_STREAM_NAME_VAR = "KVSStreamName";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final H264Encoder encoder = H264Encoder.createH264Encoder();
    private final ColorSpace SUPPORTED_COLOR_SPACE = ColorSpace.YUV420J;
    private int processedFrameNum = 0;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        //Utils.logEnvironment(request, context, gson);
        final String imageFormat = System.getenv().get(URL_DATA_FORMAT_VAR);
        processImages(request.getBody(), imageFormat);
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setIsBase64Encoded(false);
        response.setStatusCode(200);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST");
        response.setHeaders(headers);
        response.setBody("OK");

        return response;
    }


    void processImages(String jsonBody, String imageFormat) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            for (int i = 0; i < payload.getFrames().length; i++, processedFrameNum++) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                final long timestamp = payload.getTimestamps()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                KinesisVideoFrame kinesisVideoFrame = convertToFrame(bufferedImage,
                        new Date(timestamp), processedFrameNum);

                sendToKvs(kinesisVideoFrame);
            }
        } else {
            log.debug("Method body is empty. No images for processing.");
        }
    }

    String normalize(String image64base, String imageFormat) {
        return image64base.substring(imageFormat.length());
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
    KinesisVideoFrame convertToFrame(BufferedImage bufferedImage, Date timestamp, int frameIndex) {
        if (null == bufferedImage) {
            log.warn("BufferedImage is null");

            return null;

        } else {
            long timestampTimeMs = timestamp.getTime();
            Picture picture = AWTUtil.fromBufferedImage(bufferedImage, SUPPORTED_COLOR_SPACE);
            int buffSize = encoder.estimateBufferSize(picture);
            ByteBuffer byteBuffer = ByteBuffer.allocate(buffSize);
            ByteBuffer encodedBytes = encoder.encodeFrame(picture, byteBuffer).getData();
            final int flag = frameIndex % Utils.getFps() == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
            KinesisVideoFrame kinesisVideoFrame = new KinesisVideoFrame(frameIndex,
                    flag,
                    timestampTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    timestampTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    Utils.getFrameDurationInMS() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                    encodedBytes);

            return kinesisVideoFrame;
        }
    }

    private void sendToKvs(KinesisVideoFrame kinesisVideoFrame) {
        if (null == kinesisVideoFrame) {
            log.warn("KinesisVideoFrame is null");
        } else {
            log.info("Send frame {} to KVS", kinesisVideoFrame);
        }
    }
}