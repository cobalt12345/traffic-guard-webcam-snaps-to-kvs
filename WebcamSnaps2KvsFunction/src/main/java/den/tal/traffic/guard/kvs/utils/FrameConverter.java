package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import den.tal.traffic.guard.kvs.aws.WebCamMediaSourceConfiguration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_KEY_FRAME;
import static com.amazonaws.kinesisvideo.producer.FrameFlags.FRAME_FLAG_NONE;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;

@Log4j2
public class FrameConverter {

    private H264Encoder encoder = H264Encoder.createH264Encoder();
    private ColorSpace colorSpace;
    {
        ColorSpace[] supportedColorSpaces = encoder.getSupportedColorSpaces();
        colorSpace = supportedColorSpaces[0];
        log.debug("Supported ColorSpaces: {}", Arrays.toString(supportedColorSpaces));
        log.debug("Use color space: {}", colorSpace);
    }

    @Getter
    private WebCamMediaSourceConfiguration configuration;

    public FrameConverter(WebCamMediaSourceConfiguration configuration) {
        this.configuration = configuration;
    }

    public KinesisVideoFrame imageToKinesisFrame(BufferedImageWithTimestamp image, int counter) {
        Picture picture = AWTUtil.fromBufferedImage(image.getBufferedImage(), colorSpace);
        int buffSize = encoder.estimateBufferSize(picture);
        ByteBuffer byteBuffer = ByteBuffer.allocate(buffSize);
        ByteBuffer encodedBytes = encoder.encodeFrame(picture, byteBuffer).getData();
        final long currentTimeMs = System.currentTimeMillis();
        final int flag = counter % configuration.getFps() == 0 ? FRAME_FLAG_KEY_FRAME : FRAME_FLAG_NONE;
//        KinesisVideoFrame kinesisVideoFrame = new KinesisVideoFrame(counter,
//                flag,
//                image.getTimestamp() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
//                image.getTimestamp() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
//                configuration.getFrameDurationInMS() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
//                encodedBytes);
        KinesisVideoFrame kinesisVideoFrame = new KinesisVideoFrame(counter,
                flag,
                currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                currentTimeMs * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                configuration.getFrameDurationInMS() * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
                encodedBytes);

        return kinesisVideoFrame;
    }
}
