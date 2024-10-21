/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class SettableHistogram {
  private static final Logger LOG = LogManager.getLogger();
  private static final double[] DEFAULT_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  private final String name;
  private final Histogram histogram;

  public SettableHistogram(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      String name,
      String help,
      double... buckets) {
    this.name = name;
    this.histogram =
        Histogram.build()
            .name(category.getName().toLowerCase(Locale.ROOT) + "_" + name)
            .help(help)
            .buckets(buckets.length > 0 ? buckets : DEFAULT_BUCKETS)
            .register();
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      try {
        ((PrometheusMetricsSystem) metricsSystem)
            .addCollector(category, () -> histogramToCollector(category, this.name));
      } catch (Exception e) {
        LOG.error("Failed to add collector to PrometheusMetricsSystem", e);
      }
    }
  }

  public SettableHistogram(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      String name,
      String help,
      String[] labelNames,
      double... buckets) {
    this.name = name;
    this.histogram =
        Histogram.build()
            .name(category.getName().toLowerCase(Locale.ROOT) + "_" + name)
            .help(help)
            .labelNames(labelNames)
            .buckets(buckets.length > 0 ? buckets : DEFAULT_BUCKETS)
            .register();
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      try {
        ((PrometheusMetricsSystem) metricsSystem)
            .addCollector(category, () -> histogramToCollector(category, this.name));
      } catch (Exception e) {
        LOG.error("Failed to add collector to PrometheusMetricsSystem", e);
      }
    }
  }

  public Timer startTimer(String... labelValues) {
    if (labelValues.length > 0) {
      return histogram.labels(labelValues).startTimer();
    } else {
      return histogram.startTimer();
    }
  }

  protected Collector histogramToCollector(final MetricCategory metricCategory, final String name) {
    return new Collector() {
      @Override
      public List<MetricFamilySamples> collect() {
        return histogram.collect();
      }
    };
  }
}
