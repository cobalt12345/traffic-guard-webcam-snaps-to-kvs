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
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.io.IOUtils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.*;
import org.jcodec.containers.mkv.muxer.MKVMuxer;
import org.jcodec.containers.mkv.muxer.MKVMuxerTrack;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.RgbToYuv420p;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import static org.jcodec.common.model.TapeTimecode.ZERO_TAPE_TIMECODE;

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

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        processImages(request.getBody(), Utils.getImageFormatPrefix());

        return Utils.getResponse(200, "OK");
    }

    void processImages(String jsonBody, String imageFormat) {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            AmazonKinesisVideoPutMedia kinesisVideoPutMediaClient = Utils.getKvsPutMediaClient(Utils.getRegion(),
                    Utils.getKvsName());

            BodyPayload payload = gson.fromJson(jsonBody, BodyPayload.class);
            final MKVMuxer muxer;
            H264Encoder encoder = H264Encoder.createH264Encoder();
            RgbToYuv420p converter = new RgbToYuv420p();
            final File tmpMkvFile;
            SeekableByteChannel out = null;
            try {
                tmpMkvFile = Files.createTempFile(Utils.getMkvsFolder(), ".mkv").toFile();
                log.debug("Temp file path: {}", tmpMkvFile.getAbsolutePath());
                out = NIOUtils.writableChannel(tmpMkvFile);
                muxer = new MKVMuxer(out);
            } catch (IOException ioex) {
                log.error("Encoder failed.", ioex);

                throw new RuntimeException(ioex);
            }
            for (int i = 0; i < payload.getFrames().length; ++i, ++processedFrameNum) {
                log.debug("Process image. Batch ordinal num: {}. Absolute num: {}",
                        i, processedFrameNum);

                final String image64base = payload.getFrames()[i];
                BufferedImage bufferedImage = convertToImage(normalize(image64base, imageFormat));
                MKVMuxerTrack track = null;
                if (null == track) {
                    track = muxer.createVideoTrack(VideoCodecMeta.createVideoCodecMeta("avc1",
                            ByteBuffer.wrap(Utils.CODEC_PRIVATE_DATA_640x480_25), new Size(bufferedImage.getWidth(),
                                    bufferedImage.getHeight()), Rational.R(payload.getFrames().length, 1)),
                                "V_MPEG4/ISO/AVC");
                }
                try {
                    Picture yuvPicture = Picture.create(bufferedImage.getWidth(), bufferedImage.getHeight(),
                            encoder.getSupportedColorSpaces()[0]);

                    converter.transform(AWTUtil.fromBufferedImage(bufferedImage, ColorSpace.RGB), yuvPicture);
                    ByteBuffer bufferedFrame = ByteBuffer.allocate(bufferedImage.getWidth() * bufferedImage.getHeight() * 3);
                    VideoEncoder.EncodedFrame frame =  encoder.encodeFrame(yuvPicture, bufferedFrame);

                    track.addFrame(
                            Packet.createPacket(
                                    frame.getData(),
                                    Date.from(Instant.now()).getTime(),
                                    10000,
                            1000 / payload.getFrames().length,
                            i,
                            i == (payload.getFrames().length - 1) ? Packet.FrameType.KEY : Packet.FrameType.INTER,
                            ZERO_TAPE_TIMECODE));

                } catch (Exception e) {
                    log.error("Payload images cannot be converted into the sequence.", e);
                }
            }
            try {
                muxer.finish();
                IOUtils.closeQuietly(out);
                InputStream is = new FileInputStream(tmpMkvFile);
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
                Files.delete(tmpMkvFile.toPath());
            } catch (IOException|InterruptedException e) {
                log.error("Task finalisation failed.", e);

                throw new RuntimeException(e);
            }
        } else {
            log.debug("Method body is empty. No images for processing.");
        }
    }


    String normalize(String image64base, String imageFormat) {
        log.debug("Prefix: {} Image64base: {}", imageFormat, image64base.substring(0, imageFormat.length() + 5)
                .concat("..."));

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