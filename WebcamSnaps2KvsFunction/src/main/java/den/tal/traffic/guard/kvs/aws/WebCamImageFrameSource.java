package den.tal.traffic.guard.kvs.aws;

import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import den.tal.traffic.guard.WebCam;
import den.tal.traffic.guard.kvs.utils.BufferedImageWithTimestamp;
import den.tal.traffic.guard.kvs.utils.FrameConverter;
import lombok.extern.log4j.Log4j2;

import java.awt.image.BufferedImage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Log4j2
public class WebCamImageFrameSource {

    private WebCamMediaSourceConfiguration webCamMediaSourceConfiguration;
    private OnStreamDataAvailable onStreamDataAvailableCallback;
    private final WebCam webCam;
    private boolean isRunning;
    private ExecutorService executor = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "Film Projector"));

    private FrameConverter frameConverter;

    public WebCamImageFrameSource(WebCamMediaSourceConfiguration webCamMediaSourceConfiguration, WebCam webCam,
                                  FrameConverter frameConverter) {

        this.webCamMediaSourceConfiguration = webCamMediaSourceConfiguration;
        this.webCam = webCam;
        this.frameConverter = frameConverter;
    }

    public void onStreamDataAvailable(OnStreamDataAvailable onStreamDataAvailable) {
        onStreamDataAvailableCallback = onStreamDataAvailable;
    }

    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Frame source is already running");
        }
        isRunning = true;
        webCam.startFilming();
        startFrameGenerator(webCam.getFilm());
    }

    public void stop() {
        isRunning = false;
        webCam.stopFilming();
        stopFrameGenerator();
    }

    private void startFrameGenerator(BlockingQueue<BufferedImageWithTimestamp> film) {
        executor.submit(filmProjector(film));
    }

    private void stopFrameGenerator() {
        executor.shutdown();
    }

    private Runnable filmProjector(BlockingQueue<BufferedImageWithTimestamp> film) {
        return () -> {
            int frameIndex = 0;
            while (isRunning) {
                log.debug("Project frame");
                try {
                    BufferedImageWithTimestamp image = film.take();
                    onStreamDataAvailableCallback.onFrameDataAvailable(frameConverter.imageToKinesisFrame(image,
                            frameIndex++));

                } catch (KinesisVideoException | InterruptedException ex) {
                    log.error("Waiting on the queue was interrupted.", ex);
                }
            }

//            try {
//                TimeUnit.MILLISECONDS.sleep(1000 / webCamMediaSourceConfiguration.getFps());
//            } catch (InterruptedException iex) {
//                log.warn("Projector was interrupted by {}", Thread.currentThread().getName());
//            }
        };
    }
}
