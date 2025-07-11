package patton.vireo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries to fix the custom action values in the submission_custom_action_values
 * table.
 */
public class App {
    public static void main(String[] args) throws SQLException {
        if (args.length < 3 || args.length > 4) {
            System.out.println("Usage: JDBC_URL USER PASSWORD ?--dry-run?");
            return;
        }

        String jdbc_url = args[0];
        String user = args[1];
        String password = args[2];

        boolean dry_run = args.length == 4 && args[3].equals("--dry-run");

        if (dry_run) {
            System.out.println("Checking custom action values, no changes will be made. ");
        }

        try (Connection conn = DriverManager.getConnection(jdbc_url, user, password)) {
            PreparedStatement query_submission_stat = conn.prepareStatement("SELECT id FROM submission");
            try (ResultSet rs = query_submission_stat.executeQuery()) {
                while (rs.next()) {
                    add_missing_custom_values(conn, rs.getInt(1), dry_run);
                }
            }
        }
    }

    private static void add_missing_custom_values(Connection conn, int submission_id, boolean dry_run)
            throws SQLException {

        // Find the custom action values for the submission
        PreparedStatement query_submission_custom_action_values_stat = conn.prepareStatement(
                "SELECT custom_action_values_id FROM submission_custom_action_values WHERE submission_id=?");

        query_submission_custom_action_values_stat.setInt(1, submission_id);

        List<Integer> custom_action_values_ids = new ArrayList<>();

        try (ResultSet rs = query_submission_custom_action_values_stat.executeQuery()) {
            while (rs.next()) {
                custom_action_values_ids.add(rs.getInt(1));
            }
        }

        //Find the ids for the custom actions
        PreparedStatement query_custom_action_ids_stat = conn.prepareStatement( "SELECT id FROM custom_action_definition");

        List<Integer> custom_action_ids = new ArrayList<>();

        try (ResultSet rs = query_custom_action_ids_stat.executeQuery()) {
            while (rs.next()) {
                custom_action_ids.add(rs.getInt(1));
            }
        }

        // If there are less than the full set of custom action values, then add the missing ones
        if (custom_action_values_ids.size() < custom_action_ids.size()) {
            System.out.println("Handling submission " + submission_id + " with custom action values " + custom_action_values_ids);
            add_missing_custom_values(conn, submission_id, custom_action_values_ids, custom_action_ids, dry_run);
        }
    }

    private static void add_missing_custom_values(Connection conn, int submission_id,
            List<Integer> custom_action_values_ids, List<Integer> custom_action_ids, boolean dry_run) throws SQLException {
        PreparedStatement insert_custom_action_value_stat = conn
                .prepareStatement("INSERT INTO custom_action_value (value, definition_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);

        PreparedStatement insert_submission_custom_action_values_stat = conn.prepareStatement(
                "INSERT INTO submission_custom_action_values (submission_id, custom_action_values_id) VALUES (?, ?)");

        PreparedStatement query_custom_action_values_stat = conn
                .prepareStatement("SELECT definition_id FROM custom_action_value where id=?");

        List<Integer> missing_definition_ids = new ArrayList<>(custom_action_ids);

        // Figure out what definition ids are missing
        for (int custom_action_values_id : custom_action_values_ids) {
            query_custom_action_values_stat.setInt(1, custom_action_values_id);

            try (ResultSet rs = query_custom_action_values_stat.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException(
                            "Cannot find expected custom action value with id: " + custom_action_values_id);
                }

                int definition_id = rs.getInt(1);

                if (!missing_definition_ids.contains(definition_id)) {
                    throw new RuntimeException("Unexpected definition id: " + definition_id);
                }

                missing_definition_ids.remove((Integer)definition_id);
            }
        }

        // Add the definition ids that are missing

        for (int definition_id : missing_definition_ids) {
            insert_custom_action_value_stat.setBoolean(1, false);
            insert_custom_action_value_stat.setInt(2, definition_id);

            if (dry_run) {
                System.out.println("Would insert custom action value with definition id: " + definition_id
                        + " for submission id: " + submission_id);
                continue;
            }

            insert_custom_action_value_stat.executeUpdate();

            try (ResultSet rs = insert_custom_action_value_stat.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new RuntimeException("Failed to retrieve generated key for custom action value");
                }

                int custom_action_value_id = rs.getInt(1);

                // Now insert into submission_custom_action_values

                insert_submission_custom_action_values_stat.setInt(1, submission_id);
                insert_submission_custom_action_values_stat.setInt(2, custom_action_value_id);

                insert_submission_custom_action_values_stat.executeUpdate();
            }
        }
    }
}
