package ma.agencysuite.shared.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Fournit a Hibernate des connexions « marquees » avec l'agence courante, pour que
 * PostgreSQL applique lui-meme l'isolation (Row Level Security, migration V3).
 *
 * <p>A chaque connexion empruntee pour une session :
 * <ol>
 *   <li>{@code SET ROLE agencysuite_app} : role sans privilege particulier, soumis aux
 *       politiques RLS (le compte de connexion, proprietaire des tables, ne l'est pas) ;</li>
 *   <li>{@code app.current_agency} : l'agence courante, lue par les politiques ;</li>
 *   <li>{@code app.platform_access} : 'on' uniquement pour le perimetre plateforme.</li>
 * </ol>
 * Tout est remis a zero avant de rendre la connexion au pool.
 *
 * <p>Flyway n'utilise pas ce fournisseur : il garde les droits du proprietaire pour les migrations.
 */
public class RowLevelSecurityConnectionProvider implements MultiTenantConnectionProvider<UUID> {

    /** Role cree par la migration V3. */
    static final String APP_ROLE = "agencysuite_app";

    private static final String SET_CONTEXT =
            "SELECT set_config('app.current_agency', ?, false), set_config('app.platform_access', ?, false)";

    private final transient DataSource dataSource;

    public RowLevelSecurityConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Connexion sans agence, utilisee par Hibernate pour lire les metadonnees du schema. */
    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            reset(connection);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(UUID tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            apply(connection, tenantIdentifier);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(UUID tenantIdentifier, Connection connection) throws SQLException {
        try {
            reset(connection);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    public <X> X unwrap(Class<X> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Impossible de convertir en " + unwrapType.getName());
    }

    private static void apply(Connection connection, UUID tenantIdentifier) throws SQLException {
        boolean platform = TenantIdentifierResolver.PLATFORM.equals(tenantIdentifier);
        boolean noTenant = TenantIdentifierResolver.NO_TENANT.equals(tenantIdentifier);
        String agency = platform || noTenant ? "" : tenantIdentifier.toString();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE " + APP_ROLE);
        }
        setContext(connection, agency, platform ? "on" : "off");
    }

    private static void reset(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET ROLE");
        }
        setContext(connection, "", "off");
    }

    private static void setContext(Connection connection, String agency, String platformAccess) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            statement.setString(1, agency);
            statement.setString(2, platformAccess);
            statement.execute();
        }
    }
}
