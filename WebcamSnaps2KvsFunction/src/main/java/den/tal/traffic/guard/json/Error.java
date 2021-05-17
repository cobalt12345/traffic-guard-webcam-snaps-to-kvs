package den.tal.traffic.guard.json;

import lombok.Getter;
import lombok.Setter;

public class Error {

    public Error() {}

    public Error(String msg) {
        message = msg;
    }

    @Getter
    @Setter
    private String message;
}
