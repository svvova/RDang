/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.YamlConfiguration
 */
package org.jamhardcore.dungeons;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Model;
import org.mariadb.jdbc.MariaDbPoolDataSource;

public final class Store
implements AutoCloseable {
    private final DataSource source;
    private final AutoCloseable pool;

    public Store(YamlConfiguration config) throws Exception {
        this(Store.dataSource(config));
    }

    public Store(DataSource source) throws Exception {
        AutoCloseable a;
        this.source = source;
        this.pool = source instanceof AutoCloseable ? (a = (AutoCloseable)((Object)source)) : null;
        try (Connection db = this.connection();
             Statement st = db.createStatement();){
            if (!db.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mariadb")) {
                throw new IllegalArgumentException("JamDungeons requires MariaDB");
            }
            st.execute("CREATE TABLE IF NOT EXISTS sessions(id VARCHAR(64) PRIMARY KEY,arena VARCHAR(80) NOT NULL UNIQUE,json LONGTEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS rewards(id VARCHAR(64) PRIMARY KEY,event_id VARCHAR(64) NOT NULL UNIQUE,owner VARCHAR(36) NOT NULL,json LONGTEXT NOT NULL,INDEX rewards_owner(owner)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS outcomes(event_id VARCHAR(64) PRIMARY KEY,winner VARCHAR(36) NOT NULL,reward VARCHAR(64) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS audit(id BIGINT PRIMARY KEY AUTO_INCREMENT,at BIGINT NOT NULL,actor VARCHAR(128),action VARCHAR(64) NOT NULL,details TEXT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS jobs(id VARCHAR(128) PRIMARY KEY,due BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,status VARCHAR(16) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS vouchers(id VARCHAR(64) PRIMARY KEY,reward VARCHAR(64) NOT NULL UNIQUE,amount INT NOT NULL,state VARCHAR(16) NOT NULL,redeemer VARCHAR(36),updated_at BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS pending_returns(player VARCHAR(36) PRIMARY KEY,arena VARCHAR(80) NOT NULL,json LONGTEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
        catch (Exception ex) {
            if (this.pool != null) {
                try {
                    this.pool.close();
                }
                catch (Exception close) {
                    ex.addSuppressed(close);
                }
            }
            throw ex;
        }
    }

    private static DataSource dataSource(YamlConfiguration c) throws Exception {
        String host = Store.setting(c, "host");
        String port = Store.setting(c, "port");
        String name = Store.setting(c, "database");
        if (!host.matches("[A-Za-z0-9.:-]+") || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u0430\u0434\u0440\u0435\u0441 MariaDB");
        }
        MariaDbPoolDataSource ds = new MariaDbPoolDataSource();
        ds.setUser(Store.setting(c, "username"));
        ds.setPassword(Store.setting(c, "password"));
        ds.setUrl("jdbc:mariadb://" + host + ":" + Integer.parseInt(port) + "/" + name + "?connectTimeout=5000&socketTimeout=5000&maxPoolSize=4&minPoolSize=0&poolValidMinDelay=0&maxIdleTime=60&sslMode=" + c.getString("database.ssl-mode", "disable"));
        return ds;
    }

    private static String setting(YamlConfiguration c, String key) {
        String value = System.getProperty("jamdungeons.db." + key);
        if (value == null) {
            value = System.getenv("JAMDUNGEONS_DB_" + key.toUpperCase(Locale.ROOT));
        }
        return value == null ? Objects.toString(c.get("database." + key), "") : value;
    }

    private Connection connection() throws SQLException {
        return this.source.getConnection();
    }

    private void execute(String sql, Object ... args) {
        try (Connection db = this.connection();){
            this.execute(db, sql, args);
        }
        catch (SQLException e) {
            throw new IllegalStateException("\u041e\u0448\u0438\u0431\u043a\u0430 MariaDB: " + e.getMessage(), e);
        }
    }

    private void execute(Connection db, String sql, Object ... args) throws SQLException {
        try (PreparedStatement p = db.prepareStatement(sql);){
            for (int i = 0; i < args.length; ++i) {
                p.setObject(i + 1, args[i]);
            }
            p.executeUpdate();
        }
    }

    public void save(Model.Session s) {
        this.execute("INSERT INTO sessions(id,arena,json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE json=VALUES(json)", s.id, s.arenaId, Json.encode(s));
    }

    public void delete(Model.Session s) {
        this.execute("DELETE FROM sessions WHERE id=?", s.id);
    }

    public List<Model.Session> sessions() {
        ArrayList<Model.Session> out = new ArrayList<Model.Session>();
        try (Connection db = this.connection();
             Statement s = db.createStatement();
             ResultSet r = s.executeQuery("SELECT json FROM sessions");){
            while (r.next()) {
                out.add(Json.decode(r.getString(1), Model.Session.class));
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void reward(Model.Reward r) {
        this.execute("INSERT INTO rewards(id,event_id,owner,json) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE json=VALUES(json)", r.id, r.event, r.owner, Json.encode(r));
    }

    public Model.Reward reward(String id) {
        if (id == null) return null;
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT json FROM rewards WHERE id=?");){
            s.setString(1, id);
            try (ResultSet r = s.executeQuery();){
                if (r.next()) {
                    return Json.decode(r.getString(1), Model.Reward.class);
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    public List<Model.Reward> rewards(String owner) {
        ArrayList<Model.Reward> out = new ArrayList<Model.Reward>();
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement(owner == null ? "SELECT json FROM rewards" : "SELECT json FROM rewards WHERE owner=?");){
            if (owner != null) {
                s.setString(1, owner);
            }
            try (ResultSet r = s.executeQuery();){
                while (r.next()) {
                    out.add(Json.decode(r.getString(1), Model.Reward.class));
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void award(Model.Session session, Model.Reward reward, boolean winner) {
        try (Connection db = this.connection();){
            db.setAutoCommit(false);
            try {
                if (winner) {
                    this.execute(db, "INSERT INTO outcomes(event_id,winner,reward) VALUES(?,?,?)", session.id, reward.owner, reward.id);
                }
                this.execute(db, "INSERT INTO rewards(id,event_id,owner,json) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE json=VALUES(json)", reward.id, reward.event, reward.owner, Json.encode(reward));
                this.execute(db, "INSERT INTO sessions(id,arena,json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE json=VALUES(json)", session.id, session.arenaId, Json.encode(session));
                db.commit();
            }
            catch (Exception ex) {
                try {
                    db.rollback();
                }
                catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
        catch (Exception ex) {
            throw new IllegalStateException("\u041d\u0430\u0433\u0440\u0430\u0434\u0430 \u043d\u0435 \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0435\u043d\u0430: " + ex.getMessage(), ex);
        }
    }

    public void audit(String actor, String action, String details) {
        this.execute("INSERT INTO audit(at,actor,action,details) VALUES(?,?,?,?)", System.currentTimeMillis(), actor, action, details);
    }

    public List<String> audit(int count) {
        ArrayList<String> out = new ArrayList<String>();
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT at,actor,action,details FROM audit ORDER BY id DESC LIMIT ?");){
            s.setInt(1, Math.min(100, count));
            try (ResultSet r = s.executeQuery();){
                while (r.next()) {
                    out.add(r.getLong(1) + " | " + r.getString(2) + " | " + r.getString(3) + " | " + r.getString(4));
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public boolean jobExists(String id) {
        if (id == null) return false;
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT 1 FROM jobs WHERE id=?");){
            s.setString(1, id);
            try (ResultSet r = s.executeQuery();){
                return r.next();
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void job(String id, long due, String kind) {
        this.execute("INSERT IGNORE INTO jobs(id,due,kind,status) VALUES(?,?,?,'PENDING')", id, due, kind);
    }

    public void jobStatus(String id, String status) {
        this.execute("UPDATE jobs SET status=? WHERE id=?", status, id);
    }

    public List<Job> jobs(long now) {
        ArrayList<Job> out = new ArrayList<Job>();
        try (Connection db = this.connection();
             PreparedStatement p = db.prepareStatement("SELECT id,due,kind FROM jobs WHERE status='PENDING' AND due<=? ORDER BY due");){
            p.setLong(1, now);
            try (ResultSet r = p.executeQuery();){
                while (r.next()) {
                    out.add(new Job(r.getString(1), r.getLong(2), r.getString(3)));
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public void recoverDeliveries() {
        for (Model.Reward r : this.rewards(null)) {
            boolean changed = false;
            if (r.coinState == Model.Delivery.IN_FLIGHT) {
                r.coinState = Model.Delivery.REVIEW;
                changed = true;
            }
            if (r.caseState == Model.Delivery.IN_FLIGHT) {
                r.caseState = Model.Delivery.REVIEW;
                changed = true;
            }
            if (r.petEggState == Model.Delivery.IN_FLIGHT) {
                r.petEggState = Model.Delivery.REVIEW;
                changed = true;
            }
            for (Model.GiftItem i : r.items) {
                if (i.state != Model.Delivery.IN_FLIGHT) continue;
                i.state = Model.Delivery.REVIEW;
                changed = true;
            }
            if (!changed) continue;
            this.reward(r);
        }
    }

    private Model.Voucher mapVoucher(ResultSet r) throws SQLException {
        Model.Voucher v = new Model.Voucher();
        v.id = r.getString("id");
        v.reward = r.getString("reward");
        v.amount = r.getInt("amount");
        v.state = Model.Delivery.valueOf(r.getString("state"));
        v.redeemer = r.getString("redeemer");
        return v;
    }

    public Model.Voucher voucher(String id) {
        if (id == null) return null;
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT id,reward,amount,state,redeemer FROM vouchers WHERE id=?");){
            s.setString(1, id);
            try (ResultSet r = s.executeQuery();){
                if (r.next()) {
                    return this.mapVoucher(r);
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    public Model.Voucher voucherForReward(String reward) {
        if (reward == null) return null;
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT id,reward,amount,state,redeemer FROM vouchers WHERE reward=?");){
            s.setString(1, reward);
            try (ResultSet r = s.executeQuery();){
                if (r.next()) {
                    return this.mapVoucher(r);
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    public void createVoucher(Model.Voucher v) {
        this.execute("INSERT INTO vouchers(id,reward,amount,state,redeemer,updated_at) VALUES(?,?,?,'PENDING',NULL,?)", v.id, v.reward, v.amount, System.currentTimeMillis());
    }

    /*
     * Enabled aggressive exception aggregation
     */
    public boolean lockVoucher(String id, UUID player) {
        try (Connection db = this.connection();){
            boolean bl;
            block14: {
                PreparedStatement st = db.prepareStatement("UPDATE vouchers SET state='IN_FLIGHT',redeemer=?,updated_at=? WHERE id=? AND state='PENDING'");
                try {
                    st.setString(1, player.toString());
                    st.setLong(2, System.currentTimeMillis());
                    st.setString(3, id);
                    boolean bl2 = bl = st.executeUpdate() == 1;
                    if (st == null) break block14;
                }
                catch (Throwable throwable) {
                    if (st != null) {
                        try {
                            st.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                st.close();
            }
            return bl;
        }
        catch (SQLException ex) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043a\u0440\u0435\u043f\u0438\u0442\u044c \u043c\u043e\u043d\u0435\u0442\u043a\u0443", ex);
        }
    }

    public void voucherState(String id, Model.Delivery state) {
        this.execute("UPDATE vouchers SET state=?,updated_at=? WHERE id=?", state.name(), System.currentTimeMillis(), id);
    }

    public void recoverVouchers() {
        this.execute("UPDATE vouchers SET state='REVIEW' WHERE state='IN_FLIGHT'", new Object[0]);
    }

    public void rememberReturn(UUID player, String arena, Model.Point exit) {
        this.execute("INSERT INTO pending_returns(player,arena,json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE arena=VALUES(arena),json=VALUES(json)", player.toString(), arena, Json.encode(exit));
    }

    public ReturnPoint pendingReturn(UUID player) {
        if (player == null) return null;
        try (Connection db = this.connection();
             PreparedStatement s = db.prepareStatement("SELECT arena,json FROM pending_returns WHERE player=?");){
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery();){
                if (r.next()) {
                    String arena = r.getString(1);
                    Model.Point exit = Json.decode(r.getString(2), Model.Point.class);
                    return new ReturnPoint(arena, exit);
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    public void clearReturn(UUID player) {
        this.execute("DELETE FROM pending_returns WHERE player=?", player.toString());
    }

    @Override
    public void close() throws SQLException {
        if (this.pool != null) {
            try {
                this.pool.close();
            }
            catch (Exception ex) {
                throw new SQLException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043a\u0440\u044b\u0442\u044c \u043f\u0443\u043b MariaDB", ex);
            }
        }
    }

    public record Job(String id, long due, String kind) {
    }

    public record ReturnPoint(String arena, Model.Point exit) {
    }
}

