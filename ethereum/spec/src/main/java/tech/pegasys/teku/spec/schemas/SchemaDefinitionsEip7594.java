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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.features.Eip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.MatrixEntrySchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.eip7594.MetadataMessageSchemaEip7594;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsEip7594 {

  private final CellSchema cellSchema;
  private final DataColumnSchema dataColumnSchema;
  private final DataColumnSidecarSchema dataColumnSidecarSchema;
  private final MatrixEntrySchema matrixEntrySchema;
  private final DataColumnSidecarsByRootRequestMessageSchema
      dataColumnSidecarsByRootRequestMessageSchema;
  private final DataColumnSidecarsByRangeRequestMessage
          .DataColumnSidecarsByRangeRequestMessageSchema
      dataColumnSidecarsByRangeRequestMessageSchema;
  private final MetadataMessageSchemaEip7594 metadataMessageSchema;

  public SchemaDefinitionsEip7594(final SchemaRegistry schemaRegistry) {
    final SpecConfigElectra specConfig = SpecConfigElectra.required(schemaRegistry.getSpecConfig());
    final Eip7594 featureConfig = Eip7594.required(specConfig);
    this.cellSchema = new CellSchema(featureConfig);
    this.dataColumnSchema = new DataColumnSchema(featureConfig, specConfig);
    this.dataColumnSidecarSchema =
        DataColumnSidecarSchema.create(
            SignedBeaconBlockHeader.SSZ_SCHEMA, dataColumnSchema, featureConfig, specConfig);
    this.matrixEntrySchema = MatrixEntrySchema.create(cellSchema);
    this.dataColumnSidecarsByRootRequestMessageSchema =
        new DataColumnSidecarsByRootRequestMessageSchema(featureConfig);
    this.dataColumnSidecarsByRangeRequestMessageSchema =
        new DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema(
            featureConfig);

    this.metadataMessageSchema = new MetadataMessageSchemaEip7594(specConfig);
  }

  public static SchemaDefinitionsEip7594 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip7594,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsEip7594.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip7594) schemaDefinitions;
  }

  public MetadataMessageSchemaEip7594 getMetadataMessageSchema() {
    return metadataMessageSchema;
  }

  public CellSchema getCellSchema() {
    return cellSchema;
  }

  public DataColumnSchema getDataColumnSchema() {
    return dataColumnSchema;
  }

  public DataColumnSidecarSchema getDataColumnSidecarSchema() {
    return dataColumnSidecarSchema;
  }

  public MatrixEntrySchema getMatrixEntrySchema() {
    return matrixEntrySchema;
  }

  public DataColumnSidecarsByRootRequestMessageSchema
      getDataColumnSidecarsByRootRequestMessageSchema() {
    return dataColumnSidecarsByRootRequestMessageSchema;
  }

  public DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
      getDataColumnSidecarsByRangeRequestMessageSchema() {
    return dataColumnSidecarsByRangeRequestMessageSchema;
  }
}
