package den.tal.traffic.guard;

import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.json.BodyPayload;
import den.tal.traffic.guard.kvs.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.png.PNGEncoder;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.jcodec.containers.mkv.muxer.MKVMuxer;
import org.jcodec.containers.mkv.muxer.MKVMuxerTrack;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.RgbToYuv420p;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Current lambda function processes JPEG images, converts them to h264 frames and sends to AWS.
 *
 * @author Denis Talochkin
 */
@Slf4j
public class WebcamStreamProcessor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
{
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private int processedFrameNum = 0;

    private PNGEncoder pngEncoder = new PNGEncoder();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        AmazonKinesisVideoPutMedia kinesisVideoPutMediaClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                Utils.getKvsName());

        processImages(request.getBody(), Utils.getImageFormatPrefix(), Utils.getProcessor(kinesisVideoPutMediaClient));

        return Utils.getResponse(200, "OK");
    }

    void processImages(String jsonBody, String imageFormat, Consumer<InputStream> processor) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            Picture[] pictures = new Picture[payload.getFrames().length];
            int bufferSize = 0;
            for (int i = 0; i < payload.getFrames().length; ++i, ++processedFrameNum) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                Picture picture = AWTUtil.fromBufferedImage(bufferedImage, ColorSpace.RGB);
                pictures[i] = picture;
                bufferSize += pngEncoder.estimateBufferSize(pictures[i]);
            }

            try {
                ByteBuffer allocatedBuffer = ByteBuffer.allocate(bufferSize);
                SequenceEncoder encoder = SequenceEncoder.createWithFps(new ByteBufferSeekableByteChannel (
                        allocatedBuffer, pictures.length), Rational.ONE);

                for (Picture pic : pictures) {
                    encoder.encodeNativeFrame(pic);
                }
                encoder.finish();
                InputStream is = new ByteBufferBackedInputStream(allocatedBuffer);
                processor.accept(is);
            } catch (IOException e) {
                log.error("Payload images cannot be converted into the sequence.");
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

    private static void png2mkv(String pattern, String out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        FileChannelWrapper sink = null;
        try {
            sink = new FileChannelWrapper(fos.getChannel());
            MKVMuxer muxer = new MKVMuxer(sink);

            H264Encoder encoder = new H264Encoder();
            RgbToYuv420p transform = new RgbToYuv420p(0, 0);

            MKVMuxerTrack videoTrack = null;
            int i;
            for (i = 1;; i++) {

                BufferedImage rgb = ImageIO.read(new File(""));

                if (videoTrack == null) {
                    videoTrack = muxer.addVideoTrack(new Size(rgb.getWidth(), rgb.getHeight()), "V_MPEG4/ISO/AVC");
                    videoTrack.setTgtChunkDuration(Rational.ONE, SEC);
                }
                Picture yuv = Picture.create(rgb.getWidth(), rgb.getHeight(), encoder.getSupportedColorSpaces()[0]);
                transform.transform(AWTUtil.fromBufferedImage(rgb), yuv);
                ByteBuffer buf = ByteBuffer.allocate(rgb.getWidth() * rgb.getHeight() * 3);

                ByteBuffer ff = encoder.encodeFrame(yuv, buf);

                BlockElement se = BlockElement.keyFrame(videoTrack.trackId, i - 1, ff.array());
                videoTrack.addSampleEntry(se);
            }
            if (i == 1) {
                System.out.println("Image sequence not found");
                return;
            }
            muxer.mux();
        } finally {
            IOUtils.closeQuietly(fos);
            if (sink != null)
                sink.close();

        }
    }
}