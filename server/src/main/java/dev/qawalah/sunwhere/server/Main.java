package dev.qawalah.sunwhere.server;

public final class Main {
    public static void main(String[] args) throws Exception {
        Config c = Config.fromEnv();
        if (c.adminToken.isEmpty()) System.err.println("ADMIN_TOKEN is not set: the admin keys are off.");
        App app = new App(c);
        app.start();
        System.out.println("SunWhere server on port " + app.port() + ", data in " + c.dataDir.toAbsolutePath());
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }
}
