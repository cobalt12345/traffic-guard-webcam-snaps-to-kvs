package den.tal.traffic.guard;

import java.awt.image.BufferedImage;
import java.util.concurrent.BlockingQueue;

public interface WebCam {
    public void startFilming();
    public void stopFilming();
    public BlockingQueue<BufferedImage> getFilm();
}
