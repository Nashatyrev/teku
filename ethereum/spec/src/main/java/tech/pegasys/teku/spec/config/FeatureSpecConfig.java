package tech.pegasys.teku.spec.config;

import tech.pegasys.teku.spec.config.features.Eip7594;

import java.util.Optional;

public interface FeatureSpecConfig {
    default Optional<Eip7594> getOptionalEip7594Config() {
        return Optional.empty();
    }
}
