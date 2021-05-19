package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.services.kinesisvideo.*;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.codecs.png.PNGDecoder;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;

@Slf4j
public class Utils {

    private static final int CONNECTION_TIMEOUT_IN_MILLIS = 20_000;

    /*
     * Resolution: 640x480 25fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_640x480_25 = {
            (byte) 0x01,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xFF,
            (byte) 0xE1,
            (byte) 0x00,
            (byte) 0x09,
            (byte) 0x67,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xAD,
            (byte) 0x01,
            (byte) 0x40,
            (byte) 0x7A,
            (byte) 0x20,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x04,
            (byte) 0x68,
            (byte) 0xCE,
            (byte) 0x38,
            (byte) 0x80
    };

    public static APIGatewayProxyResponseEvent getResponse(int status, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setIsBase64Encoded(false);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,POST");
        response.setHeaders(headers);
        response.setStatusCode(status);
        response.setBody(body);

        return response;
    }

    public static void print(int level, final @Nonnull String tag, final @Nonnull String message) {
        LogLevel logLevel = LogLevel.fromInt(level);
        switch (logLevel) {
            case DEBUG:
                log.debug("{}:{} \t{}", logLevel.toString(), tag, message);

                break;
            case INFO:
                log.info("{}:{} \t{}", logLevel.toString(), tag, message);

                break;
            case ERROR:
                log.error("{}:{} \t{}", logLevel.toString(), tag, message);

                break;
            case WARN:
                log.warn("{}:{} \t{}", logLevel.toString(), tag, message);

                break;
            default:
                log.debug("{}:{} \t{}", logLevel.toString(), tag, message);
        }
    }

    public static void logEnvironment(Object event, Context context, Gson gson)
    {
        // log execution details
        log.info("ENVIRONMENT VARIABLES: " + gson.toJson(System.getenv()));
        log.info("CONTEXT: " + gson.toJson(context));
        // log event details
        log.info("EVENT: " + gson.toJson(event));
        log.info("EVENT TYPE: " + event.getClass().toString());
    }

    public static String getRegion() {

        return System.getenv().get("KVSRegion");
    }

    public static String getKvsName() {

        return System.getenv().get("KVSStreamName");
    }

    public static String getImageFormatPrefix() {
        final String imageFormat = System.getenv().get("URLDataFormat");

        return imageFormat;
    }

    public static String getMkvsFolder() {

        return System.getenv().get("GeneratedMkvsFolder");
    }

    public static AmazonKinesisVideoPutMedia getKvsPutMediaClient(String awsRegion, String kvsName) {
        AmazonKinesisVideo kvsClient = AmazonKinesisVideoClient.builder()
                .withRegion(awsRegion)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();

        String endpoint = kvsClient.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName("PUT_MEDIA").withStreamName(kvsName)).getDataEndpoint();

        final URI uri = URI.create(endpoint + "/putMedia");

        AmazonKinesisVideoPutMedia putMediaClient = AmazonKinesisVideoPutMediaClient.builder()
                .withRegion(awsRegion)
                .withEndpoint(URI.create(endpoint))
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .withConnectionTimeoutInMillis(CONNECTION_TIMEOUT_IN_MILLIS)
                .build();

        return putMediaClient;
    }

    public static void printOutDirectoryRecursive(Path pathToDir) {
        try {
            Files.walkFileTree(pathToDir, Collections.emptySet(), 69, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    log.debug("(f) {}", file);

                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    log.debug("(d) {}", dir);

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Cannot print directory content.", e);
        }
    }

    public static ByteBuffer getByteBuffer(BufferedImage bufferedImage) {
        Raster raster = bufferedImage.getRaster();
        DataBufferByte dataBufferByte = (DataBufferByte) raster.getDataBuffer();
        byte[] data = dataBufferByte.getData();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.put(data, 0, data.length);
        byteBuffer.flip();//from   w  ww .  j  ava 2s. co m

        return byteBuffer;
    }

    public static Picture decodePNG(ByteBuffer buf) throws IOException {
        PNGDecoder pngDec = new PNGDecoder();
        VideoCodecMeta codecMeta = pngDec.getCodecMeta(buf);
        Picture pic = Picture.create(codecMeta.getSize().getWidth(), codecMeta.getSize().getHeight(),
                ColorSpace.RGB);

        return pngDec.decodeFrame(buf, pic.getData());
    }

    public static BufferedImage convertToImage(String image64base) {
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
