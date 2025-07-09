package patton.vireo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class App {
    public static void main(String[] args) throws SQLException {
        if (args.length < 3) {
            System.out.println("Usage: ");
            return;
        }

        String jdbc_url = args[0];
        String user = args[1];
        String password = args[2];

        Connection conn = DriverManager.getConnection(jdbc_url, user, password);

        PreparedStatement stat = conn.prepareStatement("SELECT * FROM submission_custom_action_values");

        // Submission id -> List of custom action values ids
        Map<Integer, List<Integer>> custom_action_values_map = new HashMap<>();

        try (ResultSet rs = stat.executeQuery()) {
            while (rs.next()) {
                int submission_id = rs.getInt("submission_id");
                int custom_action_values_id = rs.getInt("custom_action_values_id");

                List<Integer> values = custom_action_values_map.putIfAbsent(submission_id, new java.util.ArrayList<>());
                values.add(custom_action_values_id);
            }
        }

        // If there are less than 4 custom action values, then add the missing ones

        for (Map.Entry<Integer, List<Integer>> entry : custom_action_values_map.entrySet()) {
            if (entry.getValue().size() < 4) {
                add_missing_custom_values(conn, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void add_missing_custom_values(Connection conn, int submission_id, List<Integer> custom_action_values_ids)
            throws SQLException {
        PreparedStatement insert_custom_action_value_stat = conn.prepareStatement(
                "INSERT INTO custom_action_values (id, value, definition_id) VALUES (?, ?, ?)");

        PreparedStatement insert_submission_custom_action_values_stat = conn.prepareStatement(
                "INSERT INTO submission_custom_action_values (submission_id, custom_action_values_id) VALUES (?, ?)");

        PreparedStatement query_custom_action_values_stat = conn
                .prepareStatement("SELECT * FROM custom_action_values where id=?");


        List<Integer> needed_definition_ids = new ArrayList<>();
        needed_definition_ids.addAll(List.of(7, 8, 9, 10));

        // Figure out what definition ids are needed
        for (int custom_action_values_id : custom_action_values_ids) {
            query_custom_action_values_stat.setInt(1, custom_action_values_id);

            try (ResultSet rs = query_custom_action_values_stat.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException(
                            "Cannot find expected custom action value with id: " + custom_action_values_id);
                }

                int definition_id = rs.getInt("definition_id");

                if (!needed_definition_ids.contains(definition_id)) {
                    throw new RuntimeException("Unexpected definition id: " + definition_id);
                }

                needed_definition_ids.remove(definition_id);
            }
        }

        // Add the definition ids that are missing

        for (int definition_id : needed_definition_ids) {
            insert_custom_action_value_stat.setInt(1, custom_action_values_ids.get(0)); // Assuming we want to add it to the
                                                                                   // first
            insert_custom_action_value_stat.setBoolean(2, false);
            insert_custom_action_value_stat.setInt(3, definition_id);
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
