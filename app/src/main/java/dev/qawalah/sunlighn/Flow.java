package dev.qawalah.sunlighn;

/**
 * The route: which tool the user is in, which body (sun or moon) it is about, and which step of the tool shows.
 * Back walks back one step, from the first step to Home, and only from Home leaves the app. Plain Java, tested.
 */
final class Flow {
    // Two tools since 3.6 (owner, 2026-11-16): Where to stand (2) was folded into Build the shot. Its number stays
    // taken, so a saved route from before opens Build the shot (decode). 3.24 (owner, 2026-11-22): three tools and Now.
    // Find the spot (4) is a new number, so an old saved route never lands in it.
    static final int HOME = 0, LINE = 1, BUILD = 3, FIND = 4, NOW = 5;
    private static final int OLD_STAND = 2;
    static final int YOU = 0, SUBJECT = 1, WHEN = 2, COMPOSE = 5, SHOT = 6, SPOTS = 7, HERE = 8;

    private static final int[][] STEPS = {
            {},
            {SUBJECT, YOU, WHEN, SHOT},     // 3.8: the far end first, then where you stand (owner, 2026-11-16)
            {},
            {SUBJECT, COMPOSE, SHOT},
            {SUBJECT, SPOTS, SHOT},          // 3.24: every spot and date, on the map
            {HERE},                          // 3.24: Now, one screen
    };
    private static final String[] TITLES = {"Where you stand", "Your subject", "When", "", "", "Build it", "Your shot",
            "Where to stand", "Now"};
    private static final String[] TOOLS = {"Home", "Line up a street", "", "Build the shot", "Find the spot", "Now"};

    int tool = HOME;
    boolean moon;
    int step;

    static int[] steps(int tool) {
        return STEPS[tool].clone();
    }

    static String title(int stepId) { return TITLES[stepId]; }

    /** A step's name in a tool: Line up a street asks for a line, not a subject, so its first step is the far end (3.24). */
    static String title(int tool, int stepId) {
        return tool == LINE && stepId == SUBJECT ? "The far end" : TITLES[stepId];
    }

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

    /** The name of the step it is on: "Where you stand", "Your shot". */
    String stepTitle() { return title(tool, current()); }

    /** What the step bar says: "2 of 3 · Build it"; a tool of one screen (Now) just its name. */
    String bar() {
        if (count() == 1) return stepTitle();
        return (step + 1) + " of " + count() + " · " + stepTitle();
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
            if (t < HOME || t > NOW) return f;
            int step = Integer.parseInt(a[2].trim());
            if (t == OLD_STAND) {                  // subject, how you want it, spots, your shot: the same places here
                t = BUILD;
                step = step == 0 ? 0 : step >= 3 ? 2 : 1;
            }
            f.tool = t;
            f.moon = "1".equals(a[1].trim());
            f.step = Math.max(0, Math.min(step, Math.max(0, f.count() - 1)));
        } catch (NumberFormatException e) {
            return new Flow();
        }
        return f;
    }
}
