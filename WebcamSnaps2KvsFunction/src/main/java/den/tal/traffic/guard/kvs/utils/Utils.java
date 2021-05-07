package den.tal.traffic.guard.kvs.utils;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
import com.amazonaws.kinesisvideo.client.KinesisVideoClient;
import com.amazonaws.kinesisvideo.client.KinesisVideoClientConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.java.client.KinesisVideoJavaClientFactory;
import com.amazonaws.kinesisvideo.producer.DeviceInfo;
import com.amazonaws.kinesisvideo.producer.StorageInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import den.tal.traffic.guard.WebCam;
import den.tal.traffic.guard.kvs.aws.WebCamMediaSource;
import den.tal.traffic.guard.kvs.aws.WebCamMediaSourceConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class Utils {
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

    public static String getAssumedRoleArn() {

        return System.getenv().get("ApiGwAssumedRole");
    }

    public static String getKvsName() {

        return System.getenv().get("KVSStreamName");
    }

    public static long getFrameDurationInMS() {
        int ms = 1000 / getFps();

        return ms;
    }

    private static AWSCredentialsProvider getStsCredentialsProvider(String roleArn) {
        RoleInfo roleInfo =
                new RoleInfo().withRoleArn(roleArn)
                        .withRoleSessionName("traffic-guard-session");

        return new STSProfileCredentialsServiceProvider(roleInfo);
    }

    public static KinesisVideoClient getKvsClient(String awsRegion, String roleArn, WebCam webCam, String kvsName)
            throws KinesisVideoException {

        KinesisVideoClient kinesisVideoClient =
                KinesisVideoJavaClientFactory.createKinesisVideoClient(KinesisVideoClientConfiguration.builder()
                                .withRegion(awsRegion).build(),
                        getDeviceInfo(),
                        Executors.newScheduledThreadPool(2));

        WebCamMediaSource webCamMediaSource = getWebCamMediaSource(webCam, kvsName);

        kinesisVideoClient.registerMediaSource(webCamMediaSource);

        return kinesisVideoClient;
    }

    private static DeviceInfo getDeviceInfo() {
        return new DeviceInfo(
                1,
                "traffic-guard-webcam-snaps-to-kvs",
                getStorageInfo(),
                10,
                new Tag[0]);
    }

    private static StorageInfo getStorageInfo() {
        return new StorageInfo(0,
                StorageInfo.DeviceStorageType.DEVICE_STORAGE_TYPE_IN_MEM,
                64 * 1024 * 1024,
                90,
                "/tmp");
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
}
