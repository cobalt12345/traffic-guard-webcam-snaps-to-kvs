package den.tal.traffic.guard;

import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
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
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>,
        WebCam
{
    private static final String URL_DATA_FORMAT_VAR = "URLDataFormat";
    private static final String QUEUE_LENGTH_VAR = "WebcamStreamQueueLength";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final H264Encoder encoder = H264Encoder.createH264Encoder();
    private final ColorSpace SUPPORTED_COLOR_SPACE = ColorSpace.YUV420J;
    private int processedFrameNum = 0;
    private boolean isFilmingStarted = false;
    private BlockingQueue<BufferedImage> queue = new ArrayBlockingQueue<>(Integer.parseInt(System.getenv()
            .get(QUEUE_LENGTH_VAR)));

    private KinesisVideoClient kinesisVideoClient;

    @Override
    public void startFilming() {
        isFilmingStarted = true;
    }

    @Override
    public void stopFilming() {
        isFilmingStarted = false;
    }

    @Override
    public BlockingQueue<BufferedImage> getFilm() {

        return queue;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (kinesisVideoClient == null) {
                kinesisVideoClient = Utils.getKvsClient(Utils.getRegion(), Utils.getAssumedRoleArn(),
                        this, Utils.getKvsName());
                kinesisVideoClient.startAllMediaSources();
            }
        } catch (KinesisVideoException kvex) {
            log.error("Cannot process request", kvex);

            throw new RuntimeException(kvex);
        }

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
                boolean imageAdded = queue.offer(bufferedImage);
                if (!imageAdded) {
                    log.warn("Image wasn't added!");
                }
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

}