package com.flamingo.ai.notebooklm.service.rag.image;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Groups spatially-related images on the same page using distance-based clustering.
 *
 * <p>This strategy uses Euclidean distance to identify images that should be kept together (e.g.,
 * icons in an architecture diagram). Images within the configured threshold distance are grouped
 * using single-linkage clustering (transitive grouping).
 *
 * <p>Active when {@code rag.image-grouping.strategy=spatial} (default).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "rag.image-grouping.strategy",
    havingValue = "spatial",
    matchIfMissing = true)
public class SpatialClusteringStrategy implements ImageGroupingStrategy {

  private final RagConfig ragConfig;

  @Override
  public List<ExtractedImage> groupImages(List<ExtractedImage> images) {
    if (images == null || images.isEmpty()) {
      return images;
    }

    float threshold = ragConfig.getImageGrouping().getSpatial().getThreshold();
    int minGroupSize = ragConfig.getImageGrouping().getSpatial().getMinGroupSize();

    log.debug(
        "Applying {} with threshold={} PDF units, minGroupSize={}",
        getStrategyName(),
        threshold,
        minGroupSize);

    // Partition images by page number (LinkedHashMap preserves insertion order)
    Map<Integer, List<ExtractedImage>> imagesByPage = new LinkedHashMap<>();
    for (ExtractedImage image : images) {
      imagesByPage.computeIfAbsent(image.pageNumber(), k -> new ArrayList<>()).add(image);
    }

    // Apply clustering per page
    List<ExtractedImage> result = new ArrayList<>();
    int nextGroupId = 0;

    for (Map.Entry<Integer, List<ExtractedImage>> entry : imagesByPage.entrySet()) {
      int pageNum = entry.getKey();
      List<ExtractedImage> pageImages = entry.getValue();

      if (pageNum < 0 || pageImages.size() < minGroupSize) {
        // Skip non-PDF images or pages with too few images
        result.addAll(pageImages);
        continue;
      }

      // Perform single-linkage clustering
      List<Set<Integer>> clusters = clusterByDistance(pageImages, threshold);

      // Assign group IDs to clusters meeting minGroupSize
      for (Set<Integer> cluster : clusters) {
        if (cluster.size() >= minGroupSize) {
          int groupId = nextGroupId++;
          log.debug(
              "Created spatial group {} with {} images on page {}",
              groupId,
              cluster.size(),
              pageNum);

          for (int imageIndex : cluster) {
            ExtractedImage original = pageImages.get(imageIndex);
            result.add(
                new ExtractedImage(
                    original.index(),
                    original.mimeType(),
                    original.data(),
                    original.width(),
                    original.height(),
                    original.altText(),
                    original.approximateOffset(),
                    original.pageNumber(),
                    original.xCoordinate(),
                    original.yCoordinate(),
                    groupId));
          }
        } else {
          // Cluster too small - keep images ungrouped
          for (int imageIndex : cluster) {
            result.add(pageImages.get(imageIndex));
          }
        }
      }
    }

    log.debug(
        "Grouped {} images into {} spatial groups using {}",
        images.size(),
        nextGroupId,
        getStrategyName());

    return result;
  }

  /**
   * Performs single-linkage clustering based on Euclidean distance.
   *
   * @param images images on the same page
   * @param threshold distance threshold in PDF units
   * @return list of clusters, where each cluster is a set of image indices
   */
  private List<Set<Integer>> clusterByDistance(List<ExtractedImage> images, float threshold) {
    int n = images.size();
    UnionFind uf = new UnionFind(n);

    // Compare all pairs of images
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        ExtractedImage img1 = images.get(i);
        ExtractedImage img2 = images.get(j);

        float distance = euclideanDistance(img1, img2);
        if (distance <= threshold) {
          uf.union(i, j);
        }
      }
    }

    // Group images by their root in the union-find structure
    Map<Integer, Set<Integer>> clusters = new HashMap<>();
    for (int i = 0; i < n; i++) {
      int root = uf.find(i);
      clusters.computeIfAbsent(root, k -> new HashSet<>()).add(i);
    }

    return new ArrayList<>(clusters.values());
  }

  /** Calculates Euclidean distance between two images. */
  private float euclideanDistance(ExtractedImage img1, ExtractedImage img2) {
    float dx = img1.xCoordinate() - img2.xCoordinate();
    float dy = img1.yCoordinate() - img2.yCoordinate();
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  @Override
  public String getStrategyName() {
    return "SpatialClusteringStrategy";
  }

  /** Union-Find (Disjoint Set Union) data structure for clustering. */
  private static class UnionFind {
    private final int[] parent;
    private final int[] rank;

    UnionFind(int size) {
      parent = new int[size];
      rank = new int[size];
      for (int i = 0; i < size; i++) {
        parent[i] = i;
        rank[i] = 0;
      }
    }

    int find(int x) {
      if (parent[x] != x) {
        parent[x] = find(parent[x]); // Path compression
      }
      return parent[x];
    }

    void union(int x, int y) {
      int rootX = find(x);
      int rootY = find(y);

      if (rootX != rootY) {
        // Union by rank
        if (rank[rootX] < rank[rootY]) {
          parent[rootX] = rootY;
        } else if (rank[rootX] > rank[rootY]) {
          parent[rootY] = rootX;
        } else {
          parent[rootY] = rootX;
          rank[rootX]++;
        }
      }
    }
  }
}
