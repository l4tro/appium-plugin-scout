package benchmark;

import org.openqa.selenium.Dimension;

public class BenchmarkConstants {
    public static final String TEST_URL = "https://open.spotify.com/";
    public static final int ITERATIONS = 1;
    public static final int ITERATION_TIME = 40; //seconds
    public static final int WARMUP_ITERATIONS = 1;
    public static final int WARMUP_TIME = 20; //seconds
    public static final int TIMEOUT = 5; //minutes
    public static final int FORKS = 3; //Rather increase this than the number of iterations
    //ideally the below should be the equivalent of the emulated device's resolution used in the Appium benchmarks,
    //especially if we use the useragent spoofing.
    public static final Dimension RESOLUTION = new Dimension(412, 922);
}
