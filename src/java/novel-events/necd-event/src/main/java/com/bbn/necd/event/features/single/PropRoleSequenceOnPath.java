package com.bbn.necd.event.features.single;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.necd.common.theory.SingleFeature;
import com.bbn.necd.event.features.EventFeatures;
import com.bbn.necd.event.propositions.PropositionEdge;

import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Created by ychan on 5/12/16.
 */
public final class PropRoleSequenceOnPath extends SingleFeature {
  private static final Logger log = LoggerFactory.getLogger(PropRoleSequenceOnPath.class);

  private PropRoleSequenceOnPath(final String featureName,
      final ImmutableTable<Symbol, Integer, Double> features,
      final BiMap<String, Integer> featureIndices, final int startIndex, final int endIndex) {
    super(featureName, features, featureIndices, startIndex, endIndex);
  }

  public static Builder builder(final String featureName, final int runningIndex, final int sparsityThreshold) {
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

        final ImmutableList<String> rolesSequence = getInterveningRoleSequenceOnPathBetweenSourceTarget(eg);

        for(final String sequence : rolesSequence) {
          final String v = featureName + DELIMITER + sequence;
          featuresCacheBuilder.put(item, v, 1.0);
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

    public PropRoleSequenceOnPath build() {
      final Set<Integer> indices = featureIndices.inverse().keySet();

      if(indices.size() > 0) {
        final int minIndex = Ordering.natural().min(indices);
        final int maxIndex = Ordering.natural().max(indices);

        return new PropRoleSequenceOnPath(featureName, features.build(), featureIndices, minIndex,
            maxIndex);
      } else {
        return new PropRoleSequenceOnPath(featureName, features.build(), featureIndices, -1, -1);
      }
    }

  }

  // the sequence of prop roles on path from source to target, excluding the roles directly connected to source and target
  public static ImmutableList<String> getInterveningRoleSequenceOnPathBetweenSourceTarget(final EventFeatures eg) {
    final ImmutableList.Builder<String> ret = ImmutableList.builder();

    final ImmutableList<PropositionEdge> propPathSourceToRoot = eg.propPathRootToSource().reverse();
    final ImmutableList<PropositionEdge> propPathRootToTarget = eg.propPathRootToTarget();

    List<String> sourceRoles = Lists.newArrayList();
    for(int i=1; i<propPathSourceToRoot.size(); i++) {
      sourceRoles.add(propPathSourceToRoot.get(i).label().name());
    }

    List<String> targetRoles = Lists.newArrayList();
    for(int i=0; i<(propPathRootToTarget.size()-1); i++) {
      targetRoles.add(propPathRootToTarget.get(i).label().name());
    }

    final String sourceRolesString = StringUtils.join(sourceRoles, "_");
    final String targetRolesString = StringUtils.join(targetRoles, "_");

    ret.add(sourceRolesString + "_" + targetRolesString);
    ret.add(sourceRolesString + "_ROOT_" + targetRolesString);

    return ret.build();
  }

}
