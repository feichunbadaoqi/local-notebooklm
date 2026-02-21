package com.flamingo.ai.notebooklm.service.rag.image;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Groups ALL images on the same page together (simpler fallback strategy).
 *
 * <p>This strategy groups all images on the same page into a single group, regardless of spatial
 * proximity. It's simpler than spatial clustering and nearly 100% accurate at preventing diagram
 * fragmentation, but may group unrelated images on busy pages.
 *
 * <p>Active when {@code rag.image-grouping.strategy=page-based}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.image-grouping.strategy", havingValue = "page-based")
public class PageBasedGroupingStrategy implements ImageGroupingStrategy {

  private final RagConfig ragConfig;

  @Override
  public List<ExtractedImage> groupImages(List<ExtractedImage> images) {
    if (images == null || images.isEmpty()) {
      return images;
    }

    int minGroupSize = ragConfig.getImageGrouping().getPageBased().getMinGroupSize();

    log.debug("Applying {} with minGroupSize={}", getStrategyName(), minGroupSize);

    // Partition images by page number (LinkedHashMap preserves insertion order)
    Map<Integer, List<ExtractedImage>> imagesByPage = new LinkedHashMap<>();
    for (ExtractedImage image : images) {
      imagesByPage.computeIfAbsent(image.pageNumber(), k -> new ArrayList<>()).add(image);
    }

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

      // Group all images on this page together
      int groupId = nextGroupId++;
      log.debug(
          "Created page-based group {} with {} images on page {}",
          groupId,
          pageImages.size(),
          pageNum);

      for (ExtractedImage original : pageImages) {
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
    }

    log.debug(
        "Grouped {} images into {} page-based groups using {}",
        images.size(),
        nextGroupId,
        getStrategyName());

    return result;
  }

  @Override
  public String getStrategyName() {
    return "PageBasedGroupingStrategy";
  }
}
