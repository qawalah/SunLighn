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
        String id, name, handle, bio;
        long created, handleChanged;
        boolean banned;
    }

    static final class Guide {
        String id, owner, ownerName, title, city, citySlug, country, summary, shotsJson;
        int month, year;
        String level = "link";
        boolean listed, hidden;
        long created, updated;
        double avg;
        int votes, reports;
        long ownerCreated;
        String ownerHandle;
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
            migrateAccounts(c);
            s.execute("CREATE TABLE IF NOT EXISTS guide(id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " title TEXT NOT NULL, city TEXT NOT NULL, city_slug TEXT NOT NULL, country TEXT NOT NULL DEFAULT '',"
                    + " month INTEGER NOT NULL, year INTEGER NOT NULL, summary TEXT NOT NULL DEFAULT '', shots TEXT NOT NULL,"
                    + " listed INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, updated INTEGER NOT NULL)");
            migrateGuides(c);
            s.execute("CREATE INDEX IF NOT EXISTS guide_city ON guide(city_slug, year DESC, month DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS follow(follower TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " followee TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE, created INTEGER NOT NULL, PRIMARY KEY(follower, followee))");
            s.execute("CREATE INDEX IF NOT EXISTS follow_followee ON follow(followee)");
            s.execute("CREATE TABLE IF NOT EXISTS friend(a TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " b TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE, state TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(a, b))");
            s.execute("CREATE TABLE IF NOT EXISTS block(blocker TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " blocked TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE, created INTEGER NOT NULL, PRIMARY KEY(blocker, blocked))");
            s.execute("CREATE TABLE IF NOT EXISTS like_(account TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,"
                    + " guide TEXT NOT NULL REFERENCES guide(id) ON DELETE CASCADE, photo INTEGER NOT NULL, created INTEGER NOT NULL,"
                    + " PRIMARY KEY(account, guide, photo))");
            s.execute("CREATE INDEX IF NOT EXISTS like_guide ON like_(guide)");
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
    Account createAccount(String name, String token, String recovery, String handle) {
        Account a = new Account();
        a.id = Text.id();
        a.name = name;
        a.created = now();
        a.bio = "";
        try (Connection c = open()) {
            a.handle = handle != null ? handle : freeHandle(c, Text.handleFrom(name));
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO account(id,name,token_hash,recovery_hash,created,handle,bio,handle_changed,banned) VALUES(?,?,?,?,?,?,'',0,0)")) {
                s.setString(1, a.id);
                s.setString(2, name);
                s.setString(3, Text.hash(token));
                s.setString(4, Text.hash(recovery));
                s.setLong(5, a.created);
                s.setString(6, a.handle);
                s.executeUpdate();
            }
            return a;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) return null;
            throw wrap(e);
        }
    }

    Account byToken(String token) {
        return account("SELECT " + ACCOUNT_COLS + " FROM account WHERE token_hash=?", Text.hash(token));
    }

    /** The account for a name and a recovery code, with a new token already stored; null when they do not match. */
    Account recover(String name, String recovery, String newToken) {
        Account a = account("SELECT " + ACCOUNT_COLS + " FROM account WHERE (name=? OR handle=?) AND recovery_hash=?", name, name.toLowerCase(java.util.Locale.ROOT), Text.hash(recovery));
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

    static final String ACCOUNT_COLS = "id,name,created,handle,COALESCE(bio,''),COALESCE(handle_changed,0),COALESCE(banned,0)";

    Account byHandle(String handle) {
        return handle == null ? null : account("SELECT " + ACCOUNT_COLS + " FROM account WHERE handle=?", handle.toLowerCase(java.util.Locale.ROOT));
    }

    /** Adds the people columns to a 0.1 database and gives every account a unique handle (the order they were made). */
    private void migrateAccounts(Connection c) throws SQLException {
        java.util.Set<String> have = new java.util.HashSet<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(account)")) {
            while (r.next()) have.add(r.getString("name"));
        }
        try (Statement s = c.createStatement()) {
            if (!have.contains("handle")) s.execute("ALTER TABLE account ADD COLUMN handle TEXT");
            if (!have.contains("bio")) s.execute("ALTER TABLE account ADD COLUMN bio TEXT NOT NULL DEFAULT ''");
            if (!have.contains("handle_changed")) s.execute("ALTER TABLE account ADD COLUMN handle_changed INTEGER NOT NULL DEFAULT 0");
            if (!have.contains("banned")) s.execute("ALTER TABLE account ADD COLUMN banned INTEGER NOT NULL DEFAULT 0");
        }
        List<String[]> todo = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,name FROM account WHERE handle IS NULL ORDER BY created,id")) {
            while (r.next()) todo.add(new String[]{r.getString(1), r.getString(2)});
        }
        for (String[] a : todo) {
            String h = freeHandle(c, Text.handleFrom(a[1]));
            try (PreparedStatement p = c.prepareStatement("UPDATE account SET handle=? WHERE id=?")) {
                p.setString(1, h);
                p.setString(2, a[0]);
                p.executeUpdate();
            }
        }
        try (Statement s = c.createStatement()) {
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS account_handle ON account(handle)");
        }
    }

    /** 0.1 guides had a listed flag; 0.2 has a level (link, public, friends). */
    private void migrateGuides(Connection c) throws SQLException {
        boolean has = false;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(guide)")) {
            while (r.next()) if ("level".equals(r.getString("name"))) has = true;
        }
        if (!has) {
            try (Statement s = c.createStatement()) {
                s.execute("ALTER TABLE guide ADD COLUMN level TEXT");
                s.execute("UPDATE guide SET level = CASE WHEN listed=1 THEN 'public' ELSE 'link' END");
            }
        }
    }

    /** The first of base, base1, base2 ... that nobody has (and that is not a reserved word). */
    private String freeHandle(Connection c, String base) throws SQLException {
        for (int i = 0; ; i++) {
            String suffix = i == 0 ? "" : String.valueOf(i);
            String h = base.length() + suffix.length() > 20 ? base.substring(0, 20 - suffix.length()) + suffix : base + suffix;
            if (!Text.validHandle(h)) continue;
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM account WHERE handle=?")) {
                p.setString(1, h);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) return h;
                }
            }
        }
    }

    /** Changes the handle, name and bio (null = leave). Throws ApiError with the reason. */
    void setProfile(Account me, String handle, String name, String bio) {
        long now = now();
        try (Connection c = open()) {
            if (handle != null && !handle.equals(me.handle)) {
                if (!Text.validHandle(handle)) throw ApiError.bad("A handle is 3 to 20 lowercase letters, digits or underscores, and not a reserved word.");
                if (me.handleChanged > 0 && now - me.handleChanged < 30L * 86400L)
                    throw ApiError.tooMany("A handle can change once in 30 days.");
                try (PreparedStatement p = c.prepareStatement("UPDATE account SET handle=?, handle_changed=? WHERE id=?")) {
                    p.setString(1, handle);
                    p.setLong(2, now);
                    p.setString(3, me.id);
                    p.executeUpdate();
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) throw new ApiError(409, "That handle is taken.");
                    throw e;
                }
            }
            if (name != null && !name.equals(me.name)) {
                try (PreparedStatement p = c.prepareStatement("UPDATE account SET name=? WHERE id=?")) {
                    p.setString(1, name);
                    p.setString(2, me.id);
                    p.executeUpdate();
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) throw new ApiError(409, "That name is taken.");
                    throw e;
                }
            }
            if (bio != null) {
                try (PreparedStatement p = c.prepareStatement("UPDATE account SET bio=? WHERE id=?")) {
                    p.setString(1, bio);
                    p.setString(2, me.id);
                    p.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
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
                a.handle = r.getString(4);
                a.bio = r.getString(5);
                a.handleChanged = r.getLong(6);
                a.banned = r.getInt(7) != 0;
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
            + "(SELECT COUNT(*) FROM report WHERE guide_id=g.id),COALESCE(g.level,'link'),a.handle";
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
        g.level = r.getString(20);
        g.ownerHandle = r.getString(21);
        g.listed = "public".equals(g.level);
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
                "INSERT INTO guide(id,owner,title,city,city_slug,country,month,year,summary,shots,listed,created,updated,level)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,"
                        + " city=excluded.city, city_slug=excluded.city_slug, country=excluded.country, month=excluded.month,"
                        + " year=excluded.year, summary=excluded.summary, shots=excluded.shots, listed=excluded.listed,"
                        + " updated=excluded.updated, level=excluded.level")) {
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
            s.setInt(11, "public".equals(g.level) ? 1 : 0);
            s.setLong(12, g.created);
            s.setLong(13, g.updated);
            s.setString(14, g.level);
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
        String where = "WHERE g.level='public' AND g.hidden=0 AND a.banned=0 AND a.created<=?" + (citySlug == null ? "" : " AND g.city_slug=?");
        String sql = "SELECT " + GUIDE_COLS + GUIDE_FROM + where + " ORDER BY g.year DESC, g.month DESC, g.updated DESC LIMIT " + limit;
        return citySlug == null ? guides(sql, holdUntilCreatedBefore) : guides(sql, holdUntilCreatedBefore, citySlug);
    }

    /** A person's public, visible guides, newest first (the hold applies as in the city pages). */
    List<Guide> publicOf(String owner, long holdUntilCreatedBefore) {
        return guides("SELECT " + GUIDE_COLS + GUIDE_FROM + "WHERE g.owner=? AND g.level='public' AND g.hidden=0 AND a.banned=0 AND a.created<=?"
                + " ORDER BY g.year DESC, g.month DESC, g.updated DESC LIMIT 100", owner, holdUntilCreatedBefore);
    }

    /** City names with how many guides each: {slug, name, count}, the most guides first. */
    List<String[]> cities(long holdUntilCreatedBefore) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(
                "SELECT g.city_slug, MIN(g.city), COUNT(*) FROM guide g JOIN account a ON a.id=g.owner"
                        + " WHERE g.level='public' AND g.hidden=0 AND a.banned=0 AND a.created<=? GROUP BY g.city_slug ORDER BY COUNT(*) DESC, MIN(g.city) LIMIT 500")) {
            s.setLong(1, holdUntilCreatedBefore);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(new String[]{r.getString(1), r.getString(2), String.valueOf(r.getInt(3))});
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    boolean accountBanned(String id) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT COALESCE(banned,0) FROM account WHERE id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getInt(1) != 0;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    // ---- follow, friends, block (people design, task 3)

    boolean areFriends(String a, String b) {
        return exists("SELECT 1 FROM friend WHERE state='accepted' AND ((a=? AND b=?) OR (a=? AND b=?))", a, b, b, a);
    }

    /** Blocked in either direction. */
    boolean blocked(String a, String b) {
        return exists("SELECT 1 FROM block WHERE (blocker=? AND blocked=?) OR (blocker=? AND blocked=?)", a, b, b, a);
    }

    boolean blockedBy(String blocker, String blocked) {
        return exists("SELECT 1 FROM block WHERE blocker=? AND blocked=?", blocker, blocked);
    }

    boolean following(String a, String b) { return exists("SELECT 1 FROM follow WHERE follower=? AND followee=?", a, b); }

    boolean pending(String from, String to) {
        return exists("SELECT 1 FROM friend WHERE state='pending' AND a=? AND b=?", from, to);
    }

    void follow(String a, String b, boolean on) {
        if (on) update("INSERT OR IGNORE INTO follow(follower,followee,created) VALUES(?,?,?)", a, b, now());
        else update("DELETE FROM follow WHERE follower=? AND followee=?", a, b);
    }

    /** Asks (or, when the other asked first, accepts). Returns "pending" or "friends". */
    String friendRequest(String a, String b) {
        if (areFriends(a, b)) return "friends";
        if (pending(b, a)) {
            update("UPDATE friend SET state='accepted' WHERE a=? AND b=?", b, a);
            return "friends";
        }
        update("INSERT OR IGNORE INTO friend(a,b,state,created) VALUES(?,?,'pending',?)", a, b, now());
        return "pending";
    }

    /** Cancels, declines or ends a friendship: whichever there is between the two. */
    void friendRemove(String a, String b) {
        update("DELETE FROM friend WHERE (a=? AND b=?) OR (a=? AND b=?)", a, b, b, a);
    }

    void block(String blocker, String blocked, boolean on) {
        if (on) {
            update("INSERT OR IGNORE INTO block(blocker,blocked,created) VALUES(?,?,?)", blocker, blocked, now());
            update("DELETE FROM follow WHERE (follower=? AND followee=?) OR (follower=? AND followee=?)", blocker, blocked, blocked, blocker);
            friendRemove(blocker, blocked);
        } else update("DELETE FROM block WHERE blocker=? AND blocked=?", blocker, blocked);
    }

    int count(String sql, Object... args) {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    private boolean exists(String sql, Object... args) { return count("SELECT COUNT(*) FROM (" + sql + ")", args) > 0; }

    /** Rows of {handle, name} for a query whose first two columns are those. */
    List<String[]> people(String sql, Object... args) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new String[]{r.getString(1), r.getString(2)});
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    /** Public stats of a person: guides (public), cities, likes received (task 4), followers, following, friends. */
    java.util.Map<String, Object> stats(String id) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("guides", count("SELECT COUNT(*) FROM guide WHERE owner=? AND level='public' AND hidden=0", id));
        m.put("cities", count("SELECT COUNT(DISTINCT city_slug) FROM guide WHERE owner=? AND level='public' AND hidden=0", id));
        m.put("likes", likesReceived(id));
        m.put("followers", count("SELECT COUNT(*) FROM follow WHERE followee=?", id));
        m.put("following", count("SELECT COUNT(*) FROM follow WHERE follower=?", id));
        m.put("friends", count("SELECT COUNT(*) FROM friend WHERE state='accepted' AND (a=? OR b=?)", id, id));
        return m;
    }

    int likesReceived(String id) {
        return count("SELECT COUNT(*) FROM like_ l JOIN guide g ON g.id=l.guide WHERE g.owner=?", id);
    }

    // ---- likes (task 4)

    void like(String account, String guide, int photo, boolean on) {
        if (on) update("INSERT OR IGNORE INTO like_(account,guide,photo,created) VALUES(?,?,?,?)", account, guide, photo, now());
        else update("DELETE FROM like_ WHERE account=? AND guide=? AND photo=?", account, guide, photo);
    }

    /** Counts for a guide: index 0 the guide, 1 to 3 its photos. */
    int[] likeCounts(String guide) {
        int[] out = new int[4];
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT photo, COUNT(*) FROM like_ WHERE guide=? GROUP BY photo")) {
            p.setString(1, guide);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) if (r.getInt(1) >= 0 && r.getInt(1) <= 3) out[r.getInt(1)] = r.getInt(2);
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    /** Which of guide / photos 1-3 this account has liked. */
    boolean[] likedBy(String guide, String account) {
        boolean[] out = new boolean[4];
        if (account == null) return out;
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT photo FROM like_ WHERE guide=? AND account=?")) {
            p.setString(1, guide);
            p.setString(2, account);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) if (r.getInt(1) >= 0 && r.getInt(1) <= 3) out[r.getInt(1)] = true;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return out;
    }

    /** Guides for the feed: public ones of people I follow, public and friends-level ones of my friends. Newest first. */
    List<Guide> feed(String me, int limit) {
        return guides("SELECT " + GUIDE_COLS + GUIDE_FROM + "WHERE g.hidden=0 AND a.banned=0 AND g.owner<>?1 AND ("
                + "(g.level='public' AND g.owner IN (SELECT followee FROM follow WHERE follower=?1))"
                + " OR (g.level IN ('public','friends') AND g.owner IN (SELECT CASE WHEN a=?1 THEN b ELSE a END FROM friend WHERE state='accepted' AND (a=?1 OR b=?1)))"
                + ") AND g.owner NOT IN (SELECT blocked FROM block WHERE blocker=?1) AND g.owner NOT IN (SELECT blocker FROM block WHERE blocked=?1)"
                + " ORDER BY g.updated DESC, g.id LIMIT " + limit, me);
    }

    /** Bans or unbans by handle; false when there is no such account. */
    boolean setBanned(String handle, boolean banned) {
        return update("UPDATE account SET banned=? WHERE handle=?", banned ? 1 : 0, handle.toLowerCase(java.util.Locale.ROOT)) > 0;
    }

    /** City names with their public guide counts, the most first (no hold: this is for the admin). */
    List<String[]> topCities(int limit) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT MIN(city), COUNT(*) FROM guide WHERE level='public' AND hidden=0"
                + " GROUP BY city_slug ORDER BY COUNT(*) DESC, MIN(city) LIMIT " + limit); ResultSet r = p.executeQuery()) {
            while (r.next()) out.add(new String[]{r.getString(1), String.valueOf(r.getInt(2))});
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
