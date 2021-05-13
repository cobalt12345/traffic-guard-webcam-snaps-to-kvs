package den.tal.traffic.guard.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public class BodyPayload {

    @Getter @Setter
    private String [] frames;

    @Getter @Setter
    private float framerate;

    @Getter @Setter
    private long timestamps [];
}
