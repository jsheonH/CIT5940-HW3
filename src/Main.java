import java.util.Arrays;

public class Main {
    public static void main(String[] args) {

        // test two datasets
        String[] files = {
                "user_likes_small.csv",
                "user_likes_large.csv"
        };

        for (String file : files) {

            System.out.println("\n===============================");
            System.out.println("Testing dataset: " + file);
            System.out.println("===============================");

            // Initialize recommender
            BookRecommender br = new BookRecommender(file);

            // ===============================
            // Test 1: Single Book Recommendation
            // ===============================
            System.out.println("\n[Single Book Recommendation]");
            System.out.println("Output: " +
                    br.singleBookRecommendation("Dune"));

            // ===============================
            // Test 2: Multi Book Recommendation
            // ===============================
            System.out.println("\n[Multi Book Recommendation]");
            System.out.println("Output: " +
                    br.multiBookRecommendation(
                            Arrays.asList("Dune", "Foundation")
                    ));

            // ===============================
            // Test 3: User-based CF
            // ===============================
            System.out.println("\n[User-based CF]");
            if (file.contains("small")) {
                System.out.println("Output: " +
                        br.userBasedRecommendation("clever_fox"));
            } else {
                System.out.println("Output: " +
                        br.userBasedRecommendation("fast_fox"));
            }

            // ===============================
            // Test 4: Shortest Path
            // ===============================
            System.out.println("\n[Shortest Path]");
            System.out.println("Output: " +
                    br.shortestPath("Dune", "The Hobbit"));
        }
    }
}