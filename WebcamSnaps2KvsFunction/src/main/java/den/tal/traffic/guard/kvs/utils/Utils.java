package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.java.auth.JavaCredentialsProviderImpl;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import den.tal.traffic.guard.WebCam;
import den.tal.traffic.guard.kvs.aws.WebCamMediaSource;
import den.tal.traffic.guard.kvs.aws.WebCamMediaSourceConfiguration;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executors;

@Slf4j
public class Utils {

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

    public static int getFps() {
        String sFps = System.getenv().get("FPS");

        return Integer.parseInt(sFps);
    }

    public static String getKvsName() {

        return System.getenv().get("KVSStreamName");
    }

    public static long getFrameDurationInMS() {
        int ms = 1000 / getFps();

        return ms;
    }

    public static KinesisVideoClient getKvsClient(String awsRegion, WebCam webCam, String kvsName)
            throws KinesisVideoException {

        KinesisVideoClient kinesisVideoClient =
                KinesisVideoJavaClientFactory.createKinesisVideoClient(
                        KinesisVideoClientConfiguration.builder()
                                .withRegion(awsRegion)
                                .withCredentialsProvider(
                                        new JavaCredentialsProviderImpl(
                                                DefaultAWSCredentialsProviderChain.getInstance()))
                                .withLogChannel(Utils::print)
                                .build(),
                        getDeviceInfo(),
                        Executors.newScheduledThreadPool(2));

        WebCamMediaSource webCamMediaSource = getWebCamMediaSource(webCam, kvsName);
        kinesisVideoClient.registerMediaSource(webCamMediaSource);
        log.debug("Start WebCamMediaSource");
        webCamMediaSource.start();

        return kinesisVideoClient;
    }



    private static DeviceInfo getDeviceInfo() {
        return new DeviceInfo(
                0,
                "traffic-guard-webcam-snaps-to-kvs",
                getStorageInfo(),
                10,
                null);
    }

    private static StorageInfo getStorageInfo() {
        return new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                256 * 1024 * 1024,
                90,
                "/opt");
    }

    private static WebCamMediaSource getWebCamMediaSource(WebCam webCam, String kvsName) {

        return new WebCamMediaSource(webCam, kvsName, getFrameConverter());
    }

    private static WebCamMediaSourceConfiguration getWebCamMediaSourceConfiguration() {

        return new WebCamMediaSourceConfiguration();
    }

    private static FrameConverter getFrameConverter() {

        return new FrameConverter(getWebCamMediaSourceConfiguration());
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
}
