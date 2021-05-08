package den.tal.traffic.guard;

import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.json.BodyPayload;
import den.tal.traffic.guard.kvs.utils.BufferedImageWithTimestamp;
import den.tal.traffic.guard.kvs.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.ColorSpace;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
    private BlockingQueue<BufferedImageWithTimestamp> queue = new ArrayBlockingQueue<>(Integer.parseInt(System.getenv()
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
    public BlockingQueue<BufferedImageWithTimestamp> getFilm() {

        return queue;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {

        if (kinesisVideoClient == null) {
            try {
                kinesisVideoClient = Utils.getKvsClient(Utils.getRegion(), this, Utils.getKvsName());
//                kinesisVideoClient.startAllMediaSources();
            } catch (KinesisVideoException kvex) {
                log.error("Cannot create KVS client", kvex);
                var response = Utils.getResponse(500, gson.toJson(kvex));

                return response;
            }
        }

        try {
            //Utils.logEnvironment(request, context, gson);
            final String imageFormat = System.getenv().get(URL_DATA_FORMAT_VAR);
            if (isFilmingStarted) {
                processImages(request.getBody(), imageFormat);
            } else {
                log.warn("Filming is not started.");
            }
        } catch (Exception ex) {
            log.error("Cannot process images", ex);
            var response = Utils.getResponse(500, gson.toJson(ex));

            return response;
        }

        return Utils.getResponse(200, "OK");
    }


    void processImages(String jsonBody, String imageFormat) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            for (int i = 0; i < payload.getFrames().length; ++i, ++processedFrameNum) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                final long timestamp = payload.getTimestamps()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                try {

                    queue.put(new BufferedImageWithTimestamp(bufferedImage, timestamp));
                    log.debug("Image processed. Batch ordinal num: {} Absolute num: {}", i, processedFrameNum);
                } catch (InterruptedException iex) {
                    log.error("Waiting on the queue was interrupted.", iex);
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