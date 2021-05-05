package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Utils {
    public static void logEnvironment(Object event, Context context, Gson gson)
    {
        // log execution details
        log.info("ENVIRONMENT VARIABLES: " + gson.toJson(System.getenv()));
        log.info("CONTEXT: " + gson.toJson(context));
        // log event details
        log.info("EVENT: " + gson.toJson(event));
        log.info("EVENT TYPE: " + event.getClass().toString());
    }

    public static int getFps() {
        String sFps = System.getenv().get("FPS");

        return Integer.parseInt(sFps);
    }

    public static long getFrameDurationInMS() {
        int ms = 1000 / getFps();

        return ms;
    }
}
