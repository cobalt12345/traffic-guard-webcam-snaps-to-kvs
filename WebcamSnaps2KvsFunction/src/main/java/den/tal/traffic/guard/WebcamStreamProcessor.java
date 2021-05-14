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
import org.jcodec.common.Codec;
import org.jcodec.common.MuxerTrack;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.model.*;
import org.jcodec.containers.mkv.boxes.EbmlMaster;
import org.jcodec.containers.mkv.muxer.MKVMuxer;
import org.jcodec.containers.mkv.muxer.MKVMuxerTrack;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.RgbToYuv420p;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.function.Consumer;

import static den.tal.traffic.guard.kvs.utils.Utils.CODEC_PRIVATE_DATA_640x480_25;

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
    private H264Encoder encoder = H264Encoder.createH264Encoder();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        AmazonKinesisVideoPutMedia kinesisVideoPutMediaClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                Utils.getKvsName());

        processImages(request.getBody(), Utils.getImageFormatPrefix(), Utils.getProcessor(kinesisVideoPutMediaClient));

        return Utils.getResponse(200, "OK");
    }

    void processImages(String jsonBody, String imageFormat, Consumer<ByteBuffer> processor) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            Picture[] pictures = new Picture[payload.getFrames().length];
            int bufferSize = 0;
            Rational rational = Rational.R(payload.getFrames().length, 1);
            for (int i = 0; i < payload.getFrames().length; ++i, ++processedFrameNum) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                Picture picture = AWTUtil.fromBufferedImage(bufferedImage, encoder.getSupportedColorSpaces()[0]);
                pictures[i] = picture;
                bufferSize += encoder.estimateBufferSize(pictures[i]);
            }

            try {
                ByteBuffer allocatedBuffer = ByteBuffer.allocate(bufferSize);
                MKVMuxer muxer = new MKVMuxer(new ByteBufferSeekableByteChannel (
                        allocatedBuffer, pictures.length));

                MuxerTrack videoTrack = null;

                for (int frameNum  = 0; frameNum < pictures.length; frameNum++) {
                    var pic = pictures[frameNum];
                    if (videoTrack == null) {
                        videoTrack = muxer.addVideoTrack(Codec.H264, VideoCodecMeta.createSimpleVideoCodecMeta(
                                new Size(pic.getWidth(), pic.getHeight()), encoder.getSupportedColorSpaces()[0]));

//                        videoTrack = muxer.addVideoTrack(Codec.H264, VideoCodecMeta.createVideoCodecMeta(
//                    "X264", ByteBuffer.wrap(CODEC_PRIVATE_DATA_640x480_25),
//                                new Size(pic.getWidth(), pic.getHeight()), Rational.R(25, 1)));
                    }
                    int pictureBufferSize = encoder.estimateBufferSize(pic);
                    ByteBuffer pictureBuffer = ByteBuffer.allocate(pictureBufferSize);
                    VideoEncoder.EncodedFrame encodedFrame = encoder.encodeFrame(pic, pictureBuffer);

                    videoTrack.addFrame(Packet.createPacket(encodedFrame.getData(), new Date().getTime(),
                            rational.getNum(), 1000 / pictures.length, frameNum,
                            frameNum == pictures.length - 1 ?
                                    Packet.FrameType.KEY : Packet.FrameType.INTER, TapeTimecode.ZERO_TAPE_TIMECODE));
                }
                muxer.finish();
                processor.accept(allocatedBuffer);
            } catch (Exception e) {
                log.error("Payload images cannot be converted into the sequence.", e);
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

//    private static void png2mkv(String pattern, String out) throws IOException {
//        FileOutputStream fos = new FileOutputStream(out);
//        FileChannelWrapper sink = null;
//        try {
//            sink = new FileChannelWrapper(fos.getChannel());
//            MKVMuxer muxer = new MKVMuxer(sink);
//
//            RgbToYuv420p transform = new RgbToYuv420p(0, 0);
//
//            MKVMuxerTrack videoTrack = null;
//            int i;
//            for (i = 1;; i++) {
//
//                BufferedImage rgb = ImageIO.read(new File(""));
//
//                if (videoTrack == null) {
//                    videoTrack = muxer.addVideoTrack(new Size(rgb.getWidth(), rgb.getHeight()), "V_MPEG4/ISO/AVC");
//                    videoTrack.setTgtChunkDuration(Rational.ONE, SEC);
//                }
//                Picture yuv = Picture.create(rgb.getWidth(), rgb.getHeight(), encoder.getSupportedColorSpaces()[0]);
//                transform.transform(AWTUtil.fromBufferedImage(rgb), yuv);
//                ByteBuffer buf = ByteBuffer.allocate(rgb.getWidth() * rgb.getHeight() * 3);
//
//                ByteBuffer ff = encoder.encodeFrame(yuv, buf);
//
//                BlockElement se = BlockElement.keyFrame(videoTrack.trackId, i - 1, ff.array());
//                videoTrack.addSampleEntry(se);
//            }
//            if (i == 1) {
//                System.out.println("Image sequence not found");
//                return;
//            }
//            muxer.mux();
//        } finally {
//            IOUtils.closeQuietly(fos);
//            if (sink != null)
//                sink.close();
//
//        }
//    }
}