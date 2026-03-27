import java.io.*;
import java.util.*;

public class BookRecommender {

    // =========================
    // Graph Structures
    // =========================

    // Co-like graph:
    // book -> (neighbor book -> weight)
    private Map<String, Map<String, Integer>> coLikeGraph;

    // User-based graph:
    // user -> set of books
    private Map<String, Set<String>> userToBooks;

    // Reverse lookup:
    // book -> set of users
    private Map<String, Set<String>> bookToUsers;

    // =========================
    // Constructor
    // =========================
    public BookRecommender(String filename) {
        coLikeGraph = new HashMap<>();
        userToBooks = new HashMap<>();
        bookToUsers = new HashMap<>();

        loadData(filename);        // Load CSV
        buildCoLikeGraph();        // Part 1
        buildUserBookGraph();      // Part 3
    }

    // =========================
    // Main Entry
    // =========================
    public static void main(String[] args) {
        // args[0]: input CSV file path
        String file = args[0];

        // args[1]: command specifying which recommendation method to run
        String command = args[1];

        BookRecommender br = new BookRecommender(file);

        // Command: single_book_mn
        // Input: a single book ID
        // Output: top 5 most similar books based on co-like graph
        if (command.equals("single_book_mn")) {
            String bookId = args[2];
            System.out.println(br.singleBookRecommendation(bookId));

        // Command: like_history_mn
        // Input: multiple book IDs representing a user's like history
        // Output: top 5 recommended books based on aggregated neighbor scores
        } else if (command.equals("like_history_mn")) {
            List<String> books = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                books.add(args[i]);
            }
            System.out.println(br.multiBookRecommendation(books));

        // Command: user_cf
        // Input: a target user ID
        // Output: top 5 recommended books based on similar users (collaborative filtering)
        } else if (command.equals("user_cf")) {
            String userId = args[2];
            System.out.println(br.userBasedRecommendation(userId));

        // Command: shortest_path
        // Input: source book ID and target book ID
        // Output: shortest path between the two books in the filtered co-like graph
        } else if (command.equals("shortest_path")) {
            String source = args[2];
            String target = args[3];
            System.out.println(br.shortestPath(source, target));
        } else {
            System.out.println("NONE");
        }
    }

    // =========================
    // Load CSV Data
    // =========================
    private void loadData(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                String user = parts[0];
                String book = parts[1];

                // Build user -> books mapping
                userToBooks.putIfAbsent(user, new HashSet<>());
                userToBooks.get(user).add(book);

                // Build book -> users mapping
                bookToUsers.putIfAbsent(book, new HashSet<>());
                bookToUsers.get(book).add(user);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================
    // Part 1: Build Co-Like Graph (books vs book)
    // =========================
    private void buildCoLikeGraph() {
        // For each user, connect all pairs of books they liked (user -> {book1, book2, book3})
        for (String user : userToBooks.keySet()) {
            List<String> books = new ArrayList<>(userToBooks.get(user));

            // user -> {book1, book2, book3} => user -> {(book1, book2), (book2, book3), (book1, book3)}
            for (int i = 0; i < books.size(); i++) {
                for (int j = i + 1; j < books.size(); j++) {
                    String b1 = books.get(i);
                    String b2 = books.get(j);

                    addEdge(b1, b2);
                    addEdge(b2, b1); // undirected
                }
            }
        }
    }

    // Helper to add/update edge weight
    // bookA → {
    //    bookB: 3,
    //    bookC: 5
    //}
    private void addEdge(String from, String to) {
        coLikeGraph.putIfAbsent(from, new HashMap<>());
        Map<String, Integer> neighbors = coLikeGraph.get(from);

        neighbors.put(to, neighbors.getOrDefault(to, 0) + 1);
    }

    // =========================
    // Part 3: User-Book Graph
    // =========================
    private void buildUserBookGraph() {
        // Already built in loadData
        // (userToBooks and bookToUsers)
    }

    // =========================
    // Part 2a: Single Book Recommendation
    // =========================
    public String singleBookRecommendation(String bookId) {

        // If the book does not exist or has no neighbors
        if (!coLikeGraph.containsKey(bookId)) {
            return "NONE";
        }

        Map<String, Integer> neighbors = coLikeGraph.get(bookId);

        // If no neighbors
        if (neighbors.isEmpty()) {
            return "NONE";
        }

        // Convert neighbors map to list for sorting
        List<Map.Entry<String, Integer>> list = new ArrayList<>(neighbors.entrySet());

        // Sort:
        // 1. Descending by weight
        // 2. Lexicographically by book ID (tie-break)
        Collections.sort(list, (a, b) -> {
            if (!a.getValue().equals(b.getValue())) {
                return b.getValue() - a.getValue(); // higher weight first
            }
            return a.getKey().compareTo(b.getKey()); // alphabetical order
        });

        // Collect top 5 results
        List<String> result = new ArrayList<>();

        for (int i = 0; i < list.size() && result.size() < 5; i++) {
            result.add(list.get(i).getKey());
        }

        // If no valid recommendations
        if (result.isEmpty()) {
            return "NONE";
        }

        // Join as comma-separated string (no spaces)
        return String.join(",", result);
    }

    // =========================
    // Part 2b: Multiple Book Recommendation
    // It recommends books to a target user by finding other users with similar tastes and suggesting books those users liked
    // =========================
    public String multiBookRecommendation(List<String> likedBooks) {

        if (likedBooks == null || likedBooks.isEmpty()) return "NONE";

        // Map to store cumulative scores for each candidate book
        Map<String, Integer> scoreMap = new HashMap<>();

        // Convert likedBooks to a set for fast lookup (to exclude later)
        Set<String> likedSet = new HashSet<>(likedBooks);

        // Step 1: Aggregate scores
        for (String book : likedBooks) {

            // Skip if the book is not in the graph
            if (!coLikeGraph.containsKey(book)) continue;

            Map<String, Integer> neighbors = coLikeGraph.get(book);

            for (Map.Entry<String, Integer> entry : neighbors.entrySet()) {
                String neighbor = entry.getKey();
                int weight = entry.getValue();

                // Skip books already liked by the user
                if (likedSet.contains(neighbor)) continue;

                // Add weight to cumulative score
                scoreMap.put(neighbor, scoreMap.getOrDefault(neighbor, 0) + weight);
            }
        }

        // Step 2: Handle no results
        if (scoreMap.isEmpty()) {
            return "NONE";
        }

        // Step 3: Sort candidates
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scoreMap.entrySet());

        Collections.sort(list, (a, b) -> {
            // Sort by descending cumulative score
            if (!a.getValue().equals(b.getValue())) {
                return b.getValue() - a.getValue();
            }
            // Tie-break: lexicographical order
            return a.getKey().compareTo(b.getKey());
        });

        // Step 4: Collect top 5
        List<String> result = new ArrayList<>();

        for (int i = 0; i < list.size() && result.size() < 5; i++) {
            result.add(list.get(i).getKey());
        }

        // Step 5: Format output
        return String.join(",", result);
    }

    // =========================
    // Part 4: User-Based Recommendation
    // It recommends books by finding users with similar reading preferences and suggesting books they liked that the target user has not read yet
    // =========================
    public String userBasedRecommendation(String targetUser) {

        // Step 1: Check if the target user exists in the dataset
        if (!userToBooks.containsKey(targetUser)) {
            return "NONE";
        }

        // Retrieve all books liked by the target user
        Set<String> targetBooks = userToBooks.get(targetUser);

        // Step 2: Compute similarity between target user and all other users
        // Key = other user, Value = number of common books
        Map<String, Integer> similarity = new HashMap<>();

        // Iterate through all users
        for (String otherUser : userToBooks.keySet()) {

            // Skip comparing the user with themselves
            if (otherUser.equals(targetUser)) continue;

            // Get the set of books liked by the other user
            Set<String> otherBooks = userToBooks.get(otherUser);

            // Count how many books both users like (intersection size)
            int common = 0;
            for (String book : otherBooks) {
                if (targetBooks.contains(book)) {
                    common++;
                }
            }

            // Only keep users with at least one shared book
            if (common > 0) {
                similarity.put(otherUser, common);
            }
        }

        // If no similar users found, return NONE
        if (similarity.isEmpty()) return "NONE";

        // Step 3: Use similarity scores to recommend books
        // Key = book, Value = accumulated score
        Map<String, Integer> scores = new HashMap<>();

        // Iterate through similar users
        for (String user : similarity.keySet()) {

            // The weight is the similarity (number of shared books)
            int weight = similarity.get(user);

            // Look at books liked by this similar user
            for (String book : userToBooks.get(user)) {

                // Skip books already liked by the target user
                if (targetBooks.contains(book)) continue;

                // Add weighted score to this candidate book
                scores.put(book, scores.getOrDefault(book, 0) + weight);
            }
        }

        // If no candidate books found, return NONE
        if (scores.isEmpty()) return "NONE";

        // Step 4: Sort books by score (descending), then by name (alphabetical)
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());

        list.sort((a, b) -> {

            // First sort by score (higher score first)
            if (!b.getValue().equals(a.getValue())) {
                return b.getValue() - a.getValue();
            }

            // If scores are equal, sort alphabetically
            return a.getKey().compareTo(b.getKey());
        });

        // Step 5: Collect top 5 recommendations
        List<String> result = new ArrayList<>();

        for (int i = 0; i < list.size() && result.size() < 5; i++) {
            result.add(list.get(i).getKey());
        }

        // Return result as comma-separated string, or NONE if empty
        return result.isEmpty() ? "NONE" : String.join(",", result);
    }

    // =========================
    // Part 5: Shortest Path (BFS)
    // =========================
    public String shortestPath(String source, String target) {

        // If either book does not exist, then no co-like graph exists
        if (!coLikeGraph.containsKey(source) || !coLikeGraph.containsKey(target)) {
            return "NONE";
        }

        // Step 1: Compute median edge weight
        List<Integer> weights = new ArrayList<>();

        // Collect all edge weights (note: edges appear twice, but it's okay)
        for (String book : coLikeGraph.keySet()) {
            for (int w : coLikeGraph.get(book).values()) {
                // Add the edge weight to the weights list
                weights.add(w);
            }
        }

        // If there are no edge weights at all, then no path can exist
        if (weights.isEmpty()) {
            return "NONE";
        }

        // Sort all weights in ascending order so we can compute the median
        Collections.sort(weights);

        // Compute median
        int n = weights.size();
        double median = weights.get((n - 1) / 2);

        // Step 2: Build filtered graph (remove edges < median)
        // Create a filtered graph: book -> list of neighbors whose edge weight is >= median
        Map<String, List<String>> filteredGraph = new HashMap<>();

        for (String book : coLikeGraph.keySet()) {

            // Make sure the current book exists in the filtered graph
            filteredGraph.putIfAbsent(book, new ArrayList<>());

            for (Map.Entry<String, Integer> entry : coLikeGraph.get(book).entrySet()) {
                // Get the neighbor book ID
                String neighbor = entry.getKey();

                // Get the edge weight between the current book and the neighbor
                int weight = entry.getValue();

                // Keep only edges with weight >= median
                if (weight >= median) {
                    filteredGraph.get(book).add(neighbor);
                }
            }
        }

        // Step 3: BFS to find shortest path
        // Create a queue for BFS traversal
        Queue<String> queue = new LinkedList<>();

        // Create a map to record each node's parent during BFS
        Map<String, String> parent = new HashMap<>(); // for path reconstruction

        // Create a set to record which books have already been visited
        Set<String> visited = new HashSet<>();

        // Add the source node to the queue to start BFS traversal
        queue.offer(source);

        // Mark the source node as visited so it won't be processed again
        visited.add(source);

        // A flag to indicate whether we have found the target node
        boolean found = false;

        // Continue BFS as long as there are nodes in the queue
        while (!queue.isEmpty()) {
            // Remove the front node from the queue (FIFO order)
            String curr = queue.poll();

            // If the current node is the target, we found the shortest path
            if (curr.equals(target)) {
                found = true;
                break;
            }

            // Get all neighbors of the current node from the filtered graph
            // If the node has no neighbors, return an empty list to avoid null issues
            for (String neighbor : filteredGraph.getOrDefault(curr, new ArrayList<>())) {

                // Only process this neighbor if it has not been visited yet
                if (!visited.contains(neighbor)) {

                    // Mark the neighbor as visited
                    visited.add(neighbor);

                    // Record the path: we reached 'neighbor' from 'curr'
                    // This is used later to reconstruct the full path
                    parent.put(neighbor, curr); // track path

                    // Add the neighbor to the queue for further exploration
                    queue.offer(neighbor);
                }
            }
        }

        // If no path found
        if (!found) {
            return "NONE";
        }

        // Step 4: Reconstruct path
        List<String> path = new ArrayList<>();

        // Start from the target node and trace backward
        String curr = target;

        // Backtrack from target → source using parent map
        while (curr != null) {

            // Add the current node to the path
            path.add(curr);

            // Move to the parent of the current node
            curr = parent.get(curr);
        }

        // Reverse to get source → target
        Collections.reverse(path);

        // Format: bookA->bookB->bookC
        return String.join("->", path);
    }
}
