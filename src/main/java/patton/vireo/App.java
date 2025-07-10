package patton.vireo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            System.out.print("Checking custom action values, no changes will be made. ");
        }

        try (Connection conn = DriverManager.getConnection(jdbc_url, user, password)) {
            PreparedStatement query_submission_stat = conn.prepareStatement("SELECT submitter_id FROM submission");

            try (ResultSet rs = query_submission_stat.executeQuery()) {
                add_missing_custom_values(conn, rs.getInt(1), dry_run);
            }

            conn.commit();
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

        // If there are less than 4 custom action values, then add the missing ones
        if (custom_action_values_ids.size() < 4) {
            System.out.println("Handling submission " + submission_id + " with " + custom_action_values_ids + " custom action values");
            add_missing_custom_values(conn, submission_id, custom_action_values_ids, dry_run);
        }
    }

    private static void add_missing_custom_values(Connection conn, int submission_id,
            List<Integer> custom_action_values_ids, boolean dry_run) throws SQLException {
        PreparedStatement insert_custom_action_value_stat = conn
                .prepareStatement("INSERT INTO custom_action_values (value, definition_id) VALUES (?, ?)");

        PreparedStatement insert_submission_custom_action_values_stat = conn.prepareStatement(
                "INSERT INTO submission_custom_action_values (submission_id, custom_action_values_id) VALUES (?, ?)");

        PreparedStatement query_custom_action_values_stat = conn
                .prepareStatement("SELECT definition_id FROM custom_action_values where id=?");

        List<Integer> missing_definition_ids = new ArrayList<>();
        missing_definition_ids.addAll(List.of(7, 8, 9, 10));

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

                missing_definition_ids.remove(definition_id);
            }
        }

        // Add the definition ids that are missing

        for (int definition_id : missing_definition_ids) {
            insert_custom_action_value_stat.setBoolean(1, false);
            insert_custom_action_value_stat.setInt(2, definition_id);

            if (dry_run) {
                System.err.println("Would insert custom action value with definition id: " + definition_id
                        + " for submission id: " + submission_id);
            } else {
                insert_custom_action_value_stat.executeUpdate();
            }

            try (ResultSet rs = insert_custom_action_value_stat.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new RuntimeException("Failed to retrieve generated key for custom action value");
                }

                int custom_action_value_id = rs.getInt(1);

                // Now insert into submission_custom_action_values

                insert_submission_custom_action_values_stat.setInt(1, submission_id);
                insert_submission_custom_action_values_stat.setInt(2, custom_action_value_id);

                if (dry_run) {
                    System.err.println("Would insert submission_custom_action_values with submission id: "
                            + submission_id + " and custom action value id: " + custom_action_value_id);
                } else {
                    insert_submission_custom_action_values_stat.executeUpdate();
                }
            }
        }
    }
}
