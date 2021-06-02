package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoAsyncClient;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMediaClient;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;

@Slf4j
public class Utils {

    private static final int CONNECTION_TIMEOUT_IN_MILLIS = 10_000;

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

    public static String getPath2ffmpeg() {
        final String path = System.getenv().get("Path2FFmpeg");

        return path;
    }

    public static int getWidth() {
        final int width = Integer.parseInt(System.getenv().get("videoWidth"));

        return width;
    }

    public static int getHeight() {
        final int height = Integer.parseInt(System.getenv("videoHeight"));

        return height;
    }

    public static AmazonKinesisVideoPutMedia getKvsPutMediaClient(String awsRegion, String kvsName) {
        var kvsClient = AmazonKinesisVideoAsyncClient.asyncBuilder()
                .withRegion(awsRegion)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();

        var endpoint = kvsClient.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName("PUT_MEDIA").withStreamName(kvsName)).getDataEndpoint();

        final var uri = URI.create(endpoint + "/putMedia");

        var putMediaClient = AmazonKinesisVideoPutMediaClient.builder()
                .withRegion(awsRegion)
                .withEndpoint(URI.create(endpoint))
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .withConnectionTimeoutInMillis(CONNECTION_TIMEOUT_IN_MILLIS)
                .build();

        return putMediaClient;
    }

    public static void printOutDirectoryRecursive(Path pathToDir) {
        try {
            Files.walkFileTree(pathToDir, Collections.emptySet(), 69, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    log.debug("(f) {}", file);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    log.debug("(d) {}", dir);

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Cannot print directory content.", e);
        }
    }
}
