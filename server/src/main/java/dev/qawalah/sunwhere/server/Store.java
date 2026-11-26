package dev.qawalah.sunwhere.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The SQLite file and every question asked of it. One connection per call: SQLite opens in microseconds. */
final class Store {
    final Path file;
    private final String url;

    static final class Account {
        String id, name;
        long created;
    }

    static final class Guide {
        String id, owner, ownerName, title, city, citySlug, country, summary, shotsJson;
        int month, year;
        boolean listed, hidden;
        long created, updated;
        double avg;
        int votes, reports;
        long ownerCreated;
    }

    Store(Path dir) throws SQLException, IOException {
        Files.createDirectories(dir);
        file = dir.resolve("sunwhere.db");
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS account(id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                    + " token_hash TEXT NOT NULL, recovery_hash TEXT NOT NULL, created INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS account_token ON account(token_hash)");
            s.execute("CREATE TABLE IF NOT EXISTS guide(id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " title TEXT NOT NULL, city TEXT NOT NULL, city_slug TEXT NOT NULL, country TEXT NOT NULL DEFAULT '',"
                    + " month INTEGER NOT NULL, year INTEGER NOT NULL, summary TEXT NOT NULL DEFAULT '', shots TEXT NOT NULL,"
                    + " listed INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, updated INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS guide_city ON guide(city_slug, year DESC, month DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS rating(guide_id TEXT NOT NULL REFERENCES guide(id) ON DELETE CASCADE,"
                    + " account_id TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE, stars INTEGER NOT NULL,"
                    + " PRIMARY KEY(guide_id, account_id))");
            s.execute("CREATE TABLE IF NOT EXISTS report(guide_id TEXT NOT NULL REFERENCES guide(id) ON DELETE CASCADE,"
                    + " account_id TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE, reason TEXT NOT NULL, created INTEGER NOT NULL,"
                    + " PRIMARY KEY(guide_id, account_id))");
        }
    }

    Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("busy_timeout", "5000");
        p.setProperty("foreign_keys", "true");
        return DriverManager.getConnection(url, p);
    }

    private static RuntimeException wrap(SQLException e) { return new IllegalStateException(e); }

    // ---- accounts

    /** Makes an account; null when the name is taken. */
    Account createAccount(String name, String token, String recovery) {
        Account a = new Account();
        a.id = Text.id();
        a.name = name;
        a.created = now();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(
                "INSERT INTO account(id,name,token_hash,recovery_hash,created) VALUES(?,?,?,?,?)")) {
            s.setString(1, a.id);
            s.setString(2, name);
            s.setString(3, Text.hash(token));
            s.setString(4, Text.hash(recovery));
            s.setLong(5, a.created);
            s.executeUpdate();
            return a;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) return null;
            throw wrap(e);
        }
    }

    Account byToken(String token) {
        return account("SELECT id,name,created FROM account WHERE token_hash=?", Text.hash(token));
    }

    /** The account for a name and a recovery code, with a new token already stored; null when they do not match. */
    Account recover(String name, String recovery, String newToken) {
        Account a = account("SELECT id,name,created FROM account WHERE name=? AND recovery_hash=?", name, Text.hash(recovery));
        if (a == null) return null;
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("UPDATE account SET token_hash=? WHERE id=?")) {
            s.setString(1, Text.hash(newToken));
            s.setString(2, a.id);
            s.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
        return a;
    }

    private Account account(String sql, String... args) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setString(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                Account a = new Account();
                a.id = r.getString(1);
                a.name = r.getString(2);
                a.created = r.getLong(3);
                return a;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /** The ids of the account's guides, then the account itself is gone with its ratings and reports. */
    List<String> deleteAccount(String id) {
        List<String> guides = new ArrayList<>();
        try (Connection c = open()) {
            try (PreparedStatement s = c.prepareStatement("SELECT id FROM guide WHERE owner=?")) {
                s.setString(1, id);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) guides.add(r.getString(1));
                }
            }
            try (PreparedStatement s = c.prepareStatement("DELETE FROM account WHERE id=?")) {
                s.setString(1, id);
                s.executeUpdate();
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return guides;
    }

    // ---- guides

    static final String GUIDE_COLS = "g.id,g.owner,a.name,g.title,g.city,g.city_slug,g.country,g.summary,g.shots,g.month,g.year,"
            + "g.listed,g.hidden,g.created,g.updated,a.created,"
            + "COALESCE((SELECT AVG(stars) FROM rating WHERE guide_id=g.id),0),"
            + "(SELECT COUNT(*) FROM rating WHERE guide_id=g.id),"
            + "(SELECT COUNT(*) FROM report WHERE guide_id=g.id)";
    static final String GUIDE_FROM = " FROM guide g JOIN account a ON a.id=g.owner ";

    private static Guide readGuide(ResultSet r) throws SQLException {
        Guide g = new Guide();
        g.id = r.getString(1);
        g.owner = r.getString(2);
        g.ownerName = r.getString(3);
        g.title = r.getString(4);
        g.city = r.getString(5);
        g.citySlug = r.getString(6);
        g.country = r.getString(7);
        g.summary = r.getString(8);
        g.shotsJson = r.getString(9);
        g.month = r.getInt(10);
        g.year = r.getInt(11);
        g.listed = r.getInt(12) != 0;
        g.hidden = r.getInt(13) != 0;
        g.created = r.getLong(14);
        g.updated = r.getLong(15);
        g.ownerCreated = r.getLong(16);
        g.avg = r.getDouble(17);
        g.votes = r.getInt(18);
        g.reports = r.getInt(19);
        return g;
    }

    Guide guide(String id) {
        List<Guide> l = guides("SELECT " + GUIDE_COLS + GUIDE_FROM + "WHERE g.id=?", id);
        return l.isEmpty() ? null : l.get(0);
    }

    List<Guide> guides(String sql, Object... args) {
        List<Guide> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(readGuide(r));
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    /** Inserts or updates; the caller has checked the owner. */
    void saveGuide(Guide g) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(
                "INSERT INTO guide(id,owner,title,city,city_slug,country,month,year,summary,shots,listed,created,updated)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,"
                        + " city=excluded.city, city_slug=excluded.city_slug, country=excluded.country, month=excluded.month,"
                        + " year=excluded.year, summary=excluded.summary, shots=excluded.shots, listed=excluded.listed,"
                        + " updated=excluded.updated")) {
            s.setString(1, g.id);
            s.setString(2, g.owner);
            s.setString(3, g.title);
            s.setString(4, g.city);
            s.setString(5, g.citySlug);
            s.setString(6, g.country);
            s.setInt(7, g.month);
            s.setInt(8, g.year);
            s.setString(9, g.summary);
            s.setString(10, g.shotsJson);
            s.setInt(11, g.listed ? 1 : 0);
            s.setLong(12, g.created);
            s.setLong(13, g.updated);
            s.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    boolean deleteGuide(String id) {
        return update("DELETE FROM guide WHERE id=?", id) > 0;
    }

    int setHidden(String id, boolean hidden) {
        return update("UPDATE guide SET hidden=? WHERE id=?", hidden ? 1 : 0, id);
    }

    /** Listed, visible guides of a city (or every city), newest first. Guides of accounts still in their hold wait. */
    List<Guide> listed(String citySlug, long holdUntilCreatedBefore, int limit) {
        String where = "WHERE g.listed=1 AND g.hidden=0 AND a.created<=?" + (citySlug == null ? "" : " AND g.city_slug=?");
        String sql = "SELECT " + GUIDE_COLS + GUIDE_FROM + where + " ORDER BY g.year DESC, g.month DESC, g.updated DESC LIMIT " + limit;
        return citySlug == null ? guides(sql, holdUntilCreatedBefore) : guides(sql, holdUntilCreatedBefore, citySlug);
    }

    /** City names with how many guides each: {slug, name, count}, the most guides first. */
    List<String[]> cities(long holdUntilCreatedBefore) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(
                "SELECT g.city_slug, MIN(g.city), COUNT(*) FROM guide g JOIN account a ON a.id=g.owner"
                        + " WHERE g.listed=1 AND g.hidden=0 AND a.created<=? GROUP BY g.city_slug ORDER BY COUNT(*) DESC, MIN(g.city) LIMIT 500")) {
            s.setLong(1, holdUntilCreatedBefore);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(new String[]{r.getString(1), r.getString(2), String.valueOf(r.getInt(3))});
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    // ---- ratings and reports

    void rate(String guide, String account, int stars) {
        update("INSERT INTO rating(guide_id,account_id,stars) VALUES(?,?,?) ON CONFLICT(guide_id,account_id) DO UPDATE SET stars=excluded.stars",
                guide, account, stars);
    }

    /** Files a report; returns how many accounts have reported the guide. */
    int report(String guide, String account, String reason) {
        update("INSERT INTO report(guide_id,account_id,reason,created) VALUES(?,?,?,?) ON CONFLICT(guide_id,account_id) DO UPDATE SET reason=excluded.reason",
                guide, account, reason, now());
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM report WHERE guide_id=?")) {
            s.setString(1, guide);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    // ---- plumbing

    private int update(String sql, Object... args) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            return s.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /** A copy of the whole database, made while it is in use (VACUUM INTO). */
    void backupTo(Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            try (Connection c = open(); Statement s = c.createStatement()) {
                s.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static long now() { return System.currentTimeMillis() / 1000L; }
}
