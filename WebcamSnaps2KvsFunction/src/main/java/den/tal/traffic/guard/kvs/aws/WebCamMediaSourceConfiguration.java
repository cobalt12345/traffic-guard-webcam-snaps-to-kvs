package den.tal.traffic.guard.kvs.aws;

import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import den.tal.traffic.guard.kvs.utils.Utils;
import lombok.Getter;
import lombok.ToString;

@ToString
public class WebCamMediaSourceConfiguration implements MediaSourceConfiguration {

    @Getter
    private String mediaSourceType = "Browser Web Camera";

    @Getter
    private String mediaSourceDescription = "Browser Web Camera streams to KVS";

    @Getter
    private String contentType = "video/h264";

    @Getter
    private int fps = Utils.getFps();

    public long getFrameDurationInMS() {
        int ms = 1000 / fps;

        return ms;
    }

    // Codec private data from AWS examples
    public static final byte[] CODEC_PRIVATE_DATA_AWS_EXAMPLES = {
            (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
            (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
            (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
            (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
            (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
            (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
            (byte) 0x1F, (byte) 0x20};

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

    /*
     * Resolution: 640x480 25fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_640x480_30 = {
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
            (byte) 0x80,
    };

    /*
     * Resolution: 800x600 25fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_800x600_25 = {
            (byte) 0x01,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xFF,
            (byte) 0xE1,
            (byte) 0x00,
            (byte) 0x0A,
            (byte) 0x67,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xAD,
            (byte) 0x01,
            (byte) 0x90,
            (byte) 0x26,
            (byte) 0xBC,
            (byte) 0xA8,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x04,
            (byte) 0x68,
            (byte) 0xCE,
            (byte) 0x38,
            (byte) 0x80
    };

    /*
     * Resolution: 800x600 30fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_800x600_30 = {
            (byte) 0x01,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xFF,
            (byte) 0xE1,
            (byte) 0x00,
            (byte) 0x0A,
            (byte) 0x67,
            (byte) 0x42,
            (byte) 0x00,
            (byte) 0x28,
            (byte) 0xAD,
            (byte) 0x01,
            (byte) 0x90,
            (byte) 0x26,
            (byte) 0xBC,
            (byte) 0xA8,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x04,
            (byte) 0x68,
            (byte) 0xCE,
            (byte) 0x38,
            (byte) 0x80,
    };

    /*
     * Resolution: 1024x768 25fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_1024x768_25 = {
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
            (byte) 0x00,
            (byte) 0x80,
            (byte) 0x0C,
            (byte) 0x22,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x04,
            (byte) 0x68,
            (byte) 0xCE,
            (byte) 0x38,
            (byte) 0x80,
    };

    /*
     * Resolution: 1024x768 30fps
     * Codec private data from den.tal.stream.sources.CodecPrivateDataTest
     */
    public static final byte[] CODEC_PRIVATE_DATA_1024x768_30 = {
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
            (byte) 0x00,
            (byte) 0x80,
            (byte) 0x0C,
            (byte) 0x22,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x04,
            (byte) 0x68,
            (byte) 0xCE,
            (byte) 0x38,
            (byte) 0x80,
    };
}
