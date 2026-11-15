package dev.qawalah.sunlighn;

/**
 * The route: which tool the user is in, which body (sun or moon) it is about, and which step of the tool shows.
 * Back walks back one step, from the first step to Home, and only from Home leaves the app. Plain Java, tested.
 */
final class Flow {
    static final int HOME = 0, LINE = 1, STAND = 2, BUILD = 3;
    static final int YOU = 0, SUBJECT = 1, WHEN = 2, CRITERIA = 3, SPOTS = 4, COMPOSE = 5, SHOT = 6;

    private static final int[][] STEPS = {
            {},
            {YOU, SUBJECT, WHEN, SHOT},
            {SUBJECT, CRITERIA, SPOTS, SHOT},
            {SUBJECT, COMPOSE, SHOT},
    };
    private static final String[] TITLES = {"Where you stand", "Your subject", "When", "How you want it",
            "Where to stand", "Build it", "Your shot"};
    private static final String[] TOOLS = {"Home", "Line up a street", "Where to stand", "Build the shot"};

    int tool = HOME;
    boolean moon;
    int step;

    static int[] steps(int tool) {
        return STEPS[tool].clone();
    }

    static String title(int stepId) { return TITLES[stepId]; }

    static String toolName(int tool) { return TOOLS[tool]; }

    int count() { return STEPS[tool].length; }

    /** The step showing, or -1 on Home. */
    int current() { return tool == HOME ? -1 : STEPS[tool][step]; }

    void start(int tool) {
        this.tool = tool;
        step = 0;
    }

    boolean next() {
        if (tool == HOME || step >= count() - 1) return false;
        step++;
        return true;
    }

    /** Jumps to a step of this tool; ignored if the tool has no such step. */
    void go(int stepId) {
        int[] s = STEPS[tool];
        for (int i = 0; i < s.length; i++) if (s[i] == stepId) step = i;
    }

    /** One step back, then Home. False when already Home: the caller leaves the app. */
    boolean back() {
        if (tool == HOME) return false;
        if (step > 0) step--;
        else tool = HOME;
        return true;
    }

    /** What the step bar says: "2 of 4 · How you want it". */
    String bar() {
        return (step + 1) + " of " + count() + " · " + TITLES[current()];
    }

    String encode() {
        return tool + "," + (moon ? 1 : 0) + "," + step;
    }

    static Flow decode(String s) {
        Flow f = new Flow();
        if (s == null) return f;
        String[] a = s.split(",");
        if (a.length != 3) return f;
        try {
            int t = Integer.parseInt(a[0].trim());
            if (t < HOME || t > BUILD) return f;
            f.tool = t;
            f.moon = "1".equals(a[1].trim());
            f.step = Math.max(0, Math.min(Integer.parseInt(a[2].trim()), Math.max(0, f.count() - 1)));
        } catch (NumberFormatException e) {
            return new Flow();
        }
        return f;
    }
}
