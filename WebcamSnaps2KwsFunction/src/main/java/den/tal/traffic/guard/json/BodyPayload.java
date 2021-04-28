package den.tal.traffic.guard.json;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

public class BodyPayload {

    @Getter @Setter
    private String [] frames;

    @Getter @Setter
    private float framerate;

    @Getter @Setter
    private Date timestamps [];
}
