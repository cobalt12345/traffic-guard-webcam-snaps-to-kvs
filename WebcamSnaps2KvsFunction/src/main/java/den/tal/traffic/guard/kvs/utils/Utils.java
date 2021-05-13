package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoAsyncClient;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMediaClient;
import com.amazonaws.services.kinesisvideo.PutMediaAckResponseHandler;
import com.amazonaws.services.kinesisvideo.model.AckEvent;
import com.amazonaws.services.kinesisvideo.model.FragmentTimecodeType;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.PutMediaRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.codecs.png.PNGEncoder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Consumer;

@Slf4j
public class Utils {

    private static final int CONNECTION_TIMEOUT_IN_MILLIS = 10_000;
    private static PNGEncoder pngEncoder = new PNGEncoder();

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

    public static int getQueueLength() {
        return Integer.parseInt(System.getenv().get("WebcamStreamQueueLength"));
    }

    public static Consumer<InputStream> getProcessor(AmazonKinesisVideoPutMedia kvsClient) {
        Consumer<InputStream> consumer = new Consumer<InputStream>() {
            @Override
            public void accept(InputStream videoFile) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                kvsClient.putMedia(new PutMediaRequest().withStreamName(getKvsName())
                                .withFragmentTimecodeType(FragmentTimecodeType.RELATIVE)
                                .withPayload(videoFile)
                                .withProducerStartTimestamp(new Date()),

                        new PutMediaAckResponseHandler() {
                            @Override
                            public void onAckEvent(AckEvent event) {
                                log.debug("Fragment acknowledged. Event: {}", gson.toJson(event));
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.error("Fragment error. {}", gson.toJson(t));
                                kvsClient.close();
                            }

                            @Override
                            public void onComplete() {
                                log.info("Fragment sent.");
                                kvsClient.close();
                            }
                        }
                );

            }
        };

        return consumer;
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
