package den.tal.traffic.guard.kvs.utils;

import lombok.Data;
import lombok.NonNull;

import java.awt.image.BufferedImage;

@Data
public class BufferedImageWithTimestamp {
    @NonNull
    private BufferedImage bufferedImage;
    @NonNull
    private long timestamp;
}
