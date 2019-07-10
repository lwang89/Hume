package com.bbn.necd.event.features.single;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.necd.common.theory.SingleFeature;
import com.bbn.necd.event.features.EventFeatures;

import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Created by ychan on 5/13/16.
 */
public final class ArgumentSectors extends SingleFeature {
  private static final Logger log = LoggerFactory.getLogger(ArgumentSectors.class);

  private ArgumentSectors(final String featureName,
      final ImmutableTable<Symbol, Integer, Double> features,
      final BiMap<String, Integer> featureIndices, final int startIndex, final int endIndex) {
    super(featureName, features, featureIndices, startIndex, endIndex);
  }

  public static Builder builder(final String featureName, final int runningIndex,
      final int sparsityThreshold) {
    return new Builder(featureName, runningIndex, sparsityThreshold);
  }

  public static class Builder {
    private final String featureName;
    private int runningIndex;
    private final ImmutableTable.Builder<Symbol, Integer, Double> features;
    private final BiMap<String, Integer> featureIndices;
    private Optional<ImmutableMap<String, Integer>> existingFeatureIndices;
    private final int sparsityThreshold;

    private Builder(final String featureName, final int runningIndex, final int sparsityThreshold) {
      this.featureName = featureName;
      this.runningIndex = runningIndex;
      this.features = ImmutableTable.builder();
      this.featureIndices = HashBiMap.create();
      this.existingFeatureIndices = Optional.absent();
      this.sparsityThreshold = sparsityThreshold;
    }

    public Builder withExistingFeatureIndices(final ImmutableMap<String, Integer> existingFeatureIndices) {
      this.existingFeatureIndices = Optional.of(existingFeatureIndices);
      return this;
    }

    public Builder extractFeatures(final ImmutableList<Symbol> ids,
        final ImmutableMap<Symbol, EventFeatures> examples) {

      final ImmutableTable.Builder<Symbol, String, Double> featuresCacheBuilder = ImmutableTable.builder();

      for (final Symbol item : ids) {
        final EventFeatures eg = examples.get(item);

        final ImmutableSet<Symbol> sourceSectors = ImmutableSet.copyOf(eg.sourceSectors());
        final ImmutableSet<Symbol> targetSectors = ImmutableSet.copyOf(eg.targetSectors());

        for(final Symbol sourceSector : sourceSectors) {
          for(final Symbol targetSector : targetSectors) {
            final String v = featureName + DELIMITER + sourceSector.asString() + DELIMITER + targetSector.asString();
            featuresCacheBuilder.put(item, v, 1.0);
          }
        }
      }
      final ImmutableTable<Symbol, String, Double> featuresCache = featuresCacheBuilder.build();

      final Multiset<String> featureCounts = getFeatureCount(featuresCache);

      for(final Table.Cell<Symbol, String, Double> cell : featuresCache.cellSet()) {
        final Symbol item = cell.getRowKey();
        final String v = cell.getColumnKey();
        final Double weight = cell.getValue();
        if(featureCounts.count(v) >= sparsityThreshold) {
          addFeatureValue(item, v, weight);
        }
      }

      return this;
    }

    private void addFeatureValue(final Symbol item, final String v, final Double weight) {
      if(existingFeatureIndices.isPresent()) {
        if(existingFeatureIndices.get().containsKey(v)) {
          final int index = existingFeatureIndices.get().get(v);
          featureIndices.put(v, index);
          features.put(item, index, weight);
        }
      } else {
        if(featureIndices.containsKey(v)) {
          features.put(item, featureIndices.get(v), weight);
        } else {
          featureIndices.put(v, runningIndex);
          features.put(item, runningIndex, weight);
          runningIndex++;
        }
      }
    }

    public ArgumentSectors build() {
      final Set<Integer> indices = featureIndices.inverse().keySet();

      if(indices.size() > 0) {
        final int minIndex = Ordering.natural().min(indices);
        final int maxIndex = Ordering.natural().max(indices);

        return new ArgumentSectors(featureName, features.build(), featureIndices, minIndex,
            maxIndex);
      } else {
        return new ArgumentSectors(featureName, features.build(), featureIndices, -1, -1);
      }
    }

  }
}
