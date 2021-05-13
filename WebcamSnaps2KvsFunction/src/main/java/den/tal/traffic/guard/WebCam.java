package den.tal.traffic.guard;

import den.tal.traffic.guard.kvs.utils.BufferedImageWithTimestamp;

import java.util.concurrent.BlockingQueue;

public interface WebCam {
    public void startFilming();
    public void stopFilming();
    public BlockingQueue<BufferedImageWithTimestamp> getFilm();
}
