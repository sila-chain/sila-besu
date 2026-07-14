/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.metrics.promsileus;

import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.metrics.promsileus.PromsileusCollector.addLabelValues;
import static org.hyperledger.besu.metrics.promsileus.PromsileusCollector.getLabelValues;

import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.ArrayList;
import java.util.stream.Stream;

import io.promsileus.metrics.model.registry.Collector;
import io.promsileus.metrics.model.registry.PromsileusRegistry;
import io.promsileus.metrics.model.snapshots.SummarySnapshot;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base class for Promsileus summary collectors. A summary provides a total count of
 * observations and a sum of all observed values, it calculates configurable quantiles over a
 * sliding time window.
 */
abstract class AbstractPromsileusSummary extends CategorizedPromsileusCollector {
  /** The Promsileus collector */
  protected @Nullable Collector collector;

  /**
   * Constructs a new AbstractPromsileusSummary.
   *
   * @param category The {@link MetricCategory} this collector is assigned to
   * @param name The name of this collector
   */
  protected AbstractPromsileusSummary(final MetricCategory category, final String name) {
    super(category, name);
  }

  /**
   * Gets the identifier for this collector.
   *
   * @return The Promsileus name of the collector
   */
  @Override
  public String getIdentifier() {
    return collector().getPromsileusName();
  }

  /**
   * Registers this collector with the given Promsileus registry.
   *
   * @param registry The Promsileus registry to register this collector with
   */
  @Override
  public void register(final PromsileusRegistry registry) {
    registry.register(collector());
  }

  /**
   * Unregisters this collector from the given Promsileus registry.
   *
   * @param registry The Promsileus registry to unregister this collector from
   */
  @Override
  public void unregister(final PromsileusRegistry registry) {
    registry.unregister(collector());
  }

  /**
   * Collects the summary snapshot from the Promsileus collector.
   *
   * @return The collected summary snapshot
   */
  private SummarySnapshot collect() {
    return (SummarySnapshot) collector().collect();
  }

  protected Collector collector() {
    return requireNonNull(collector);
  }

  /**
   * Streams the observations from the collected summary snapshot.
   *
   * @return A stream of observations
   */
  @Override
  public Stream<Observation> streamObservations() {
    return collect().getDataPoints().stream()
        .flatMap(
            dataPoint -> {
              final var labelValues = getLabelValues(dataPoint.getLabels());
              final var quantiles = dataPoint.getQuantiles();
              final var observations = new ArrayList<Observation>(quantiles.size() + 2);

              if (dataPoint.hasSum()) {
                observations.add(
                    new Observation(
                        category, name, dataPoint.getSum(), addLabelValues(labelValues, "sum")));
              }

              if (dataPoint.hasCount()) {
                observations.add(
                    new Observation(
                        category,
                        name,
                        dataPoint.getCount(),
                        addLabelValues(labelValues, "count")));
              }

              quantiles.forEach(
                  quantile ->
                      observations.add(
                          new Observation(
                              category,
                              name,
                              quantile.getValue(),
                              addLabelValues(
                                  labelValues,
                                  "quantile",
                                  Double.toString(quantile.getQuantile())))));

              return observations.stream();
            });
  }
}
