package den.tal.traffic.guard;

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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>,
        WebCam
{
    static {
        Utils.printOutDirectoryRecursive(Paths.get("/opt"));
    }
    private static final String URL_DATA_FORMAT_VAR = "URLDataFormat";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private int processedFrameNum = 0;
    private boolean isFilmingStarted = false;
    private BlockingQueue<BufferedImageWithTimestamp> queue = new ArrayBlockingQueue<>(Utils.getQueueLength());

    private static final Utils.KvsClientType clientType = Utils.getClientType();

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
        switch (clientType) {
            case JAVA_CLIENT:
                try {
                    var kinesisVideoClient = Utils.getKvsClient(Utils.getRegion(),
                            this, Utils.getKvsName());

                    kinesisVideoClient.startAllMediaSources();
                    try {
                        //Utils.logEnvironment(request, context, gson);
                        final String imageFormat = System.getenv().get(URL_DATA_FORMAT_VAR);
                        if (isFilmingStarted) {
                            processImages(request.getBody(), imageFormat, this::addImagesToQueue);
                        } else {
                            log.warn("Filming is not started.");
                        }
                    } catch (Exception ex) {
                        log.error("Cannot process images", ex);
                        var response = Utils.getResponse(500, gson.toJson(ex));

                        return response;
                    }
                } catch (KinesisVideoException kvex) {
                    log.error("Cannot create KVS client", kvex);
                    var response = Utils.getResponse(500, gson.toJson(kvex));

                    return response;
                }

                break;
            case PUT_MEDIA_CLIENT:
                var kinesisVideoClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                        Utils.getKvsName());

                try {
                    final String imageFormat = System.getenv().get(URL_DATA_FORMAT_VAR);
                    processImages(request.getBody(), imageFormat, Utils.getProcessor(kinesisVideoClient));
                } catch (Exception ex) {
                    log.error("Cannot process images", ex);
                    var response = Utils.getResponse(500, gson.toJson(ex));

                    return response;
                }
                break;
        }

        return Utils.getResponse(200, "OK");
    }

    void addImagesToQueue(BufferedImageWithTimestamp bufferedImage) {
        try {
            queue.put(bufferedImage);
        } catch (InterruptedException iex) {
            log.error("Waiting on the queue was interrupted.", iex);
        }
    }

    void processImages(String jsonBody, String imageFormat, Consumer<BufferedImageWithTimestamp> processor) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            for (int i = 0; i < payload.getFrames().length; ++i, ++processedFrameNum) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                final long timestamp = payload.getTimestamps()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                processor.accept(new BufferedImageWithTimestamp(bufferedImage, timestamp));
                log.debug("Image processed. Batch ordinal num: {} Absolute num: {}", i, processedFrameNum);
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