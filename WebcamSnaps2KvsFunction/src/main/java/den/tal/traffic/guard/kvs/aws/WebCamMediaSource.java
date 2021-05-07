package den.tal.traffic.guard.kvs.aws;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.mediasource.DefaultOnStreamDataAvailable;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import den.tal.traffic.guard.WebCam;
import den.tal.traffic.guard.kvs.utils.FrameConverter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_ANNEXB_NALS;
import static com.amazonaws.kinesisvideo.producer.StreamInfo.codecIdFromContentType;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;

@Log4j2
public class WebCamMediaSource implements MediaSource {

    private String kinesisVideoStreamName;
    private WebCam webCam;
    private FrameConverter frameConverter;
    private MediaSourceState mediaSourceState;
    private WebCamMediaSourceConfiguration webCamMediaSourceConfiguration;
    private final String trackName = "RoadTraffic";
    private MediaSourceSink mediaSourceSink;
    private WebCamImageFrameSource webCamImageFrameSource;

    public WebCamMediaSource(WebCam webCam, String kvsName, FrameConverter frameConverter) {
        this.webCam = webCam;
        kinesisVideoStreamName = kvsName;
        this.frameConverter = frameConverter;
        webCamMediaSourceConfiguration = frameConverter.getConfiguration();
    }

    @Override
    public MediaSourceState getMediaSourceState() {
        return mediaSourceState;
    }

    @Override
    public MediaSourceConfiguration getConfiguration() {

        return webCamMediaSourceConfiguration;
    }

    @Override
    public StreamInfo getStreamInfo() throws KinesisVideoException {
        return new StreamInfo(VERSION_ZERO,
                kinesisVideoStreamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                webCamMediaSourceConfiguration.getContentType(),
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                ABSOLUTE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                codecIdFromContentType(webCamMediaSourceConfiguration.getContentType()),
                trackName,
                DEFAULT_BITRATE,
                webCamMediaSourceConfiguration.getFps(),
                DEFAULT_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                WebCamMediaSourceConfiguration.CODEC_PRIVATE_DATA_640x480_25,
                new Tag[] {
                        new Tag("device", webCamMediaSourceConfiguration.getMediaSourceDescription()),
                        new Tag("stream", kinesisVideoStreamName)
                },
                NAL_ADAPTATION_ANNEXB_NALS);
    }

    @Override
    public void configure(MediaSourceConfiguration mediaSourceConfiguration) {
        if (mediaSourceConfiguration instanceof WebCamMediaSourceConfiguration) {
            WebCamMediaSourceConfiguration wcmsConf = (WebCamMediaSourceConfiguration) mediaSourceConfiguration;
            webCamMediaSourceConfiguration = wcmsConf;
            log.debug("Web Camera source configuration: {}", mediaSourceConfiguration);
        } else {

            throw new IllegalArgumentException("Configuration must be an instance of WebCamMediaSourceConfiguration");
        }
    }

    @Override
    public void initialize(@NonNull MediaSourceSink mediaSourceSink) throws KinesisVideoException {
        this.mediaSourceSink = mediaSourceSink;
        mediaSourceState = MediaSourceState.INITIALIZED;
    }

    @Override
    public void start() throws KinesisVideoException {
        log.trace("Start webcam media source...");
        webCamImageFrameSource = new WebCamImageFrameSource(webCamMediaSourceConfiguration, webCam, frameConverter);
        webCamImageFrameSource.onStreamDataAvailable(new DefaultOnStreamDataAvailable(mediaSourceSink));
        webCamImageFrameSource.start();
        mediaSourceState = MediaSourceState.RUNNING;
    }

    @Override
    public void stop() throws KinesisVideoException {
        log.trace("Stop webcam media source...");
        if (webCamImageFrameSource != null) {
            webCamImageFrameSource.stop();
        }
        try {
            if (null != mediaSourceSink && null != mediaSourceSink.getProducerStream()) {
                mediaSourceSink.getProducerStream().stopStreamSync();
            }
        } finally {
            mediaSourceState = MediaSourceState.STOPPED;
        }
    }

    @Override
    public boolean isStopped() {
        return mediaSourceState == MediaSourceState.STOPPED;
    }

    @Override
    public void free() throws KinesisVideoException {
        log.trace("Free webcam media source...");
    }

    @Override
    public MediaSourceSink getMediaSourceSink() {
        return mediaSourceSink;
    }

    @Override
    public StreamCallbacks getStreamCallbacks() {
        return null;
    }
}
