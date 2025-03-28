/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.fs.FileStatus;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.util.ByteBuffers;

public class FileMetadata {
  private FileMetadata() {}

  public static Builder deleteFileBuilder(PartitionSpec spec) {
    return new Builder(spec);
  }

  public static class Builder {
    private final PartitionSpec spec;
    private final boolean isPartitioned;
    private final int specId;
    private FileContent content = null;
    private int[] equalityFieldIds = null;
    private PartitionData partitionData;
    private String filePath = null;
    private FileFormat format = null;
    private long recordCount = -1L;
    private long fileSizeInBytes = -1L;

    // optional fields
    private Map<Integer, Long> columnSizes = null;
    private Map<Integer, Long> valueCounts = null;
    private Map<Integer, Long> nullValueCounts = null;
    private Map<Integer, Long> nanValueCounts = null;
    private Map<Integer, ByteBuffer> lowerBounds = null;
    private Map<Integer, ByteBuffer> upperBounds = null;
    private ByteBuffer keyMetadata = null;
    private Integer sortOrderId = null;
    private List<Long> splitOffsets = null;
    private String referencedDataFile = null;
    private Long contentOffset = null;
    private Long contentSizeInBytes = null;

    Builder(PartitionSpec spec) {
      this.spec = spec;
      this.specId = spec.specId();
      this.isPartitioned = spec.isPartitioned();
      this.partitionData = isPartitioned ? DataFiles.newPartitionData(spec) : null;
    }

    public void clear() {
      if (isPartitioned) {
        partitionData.clear();
      }
      this.filePath = null;
      this.format = null;
      this.recordCount = -1L;
      this.fileSizeInBytes = -1L;
      this.columnSizes = null;
      this.valueCounts = null;
      this.nullValueCounts = null;
      this.nanValueCounts = null;
      this.lowerBounds = null;
      this.upperBounds = null;
      this.sortOrderId = null;
    }

    public Builder copy(DeleteFile toCopy) {
      if (isPartitioned) {
        Preconditions.checkState(
            specId == toCopy.specId(), "Cannot copy a DeleteFile with a different spec");
        this.partitionData = DataFiles.copyPartitionData(spec, toCopy.partition(), partitionData);
      }
      this.content = toCopy.content();
      this.filePath = toCopy.location();
      this.format = toCopy.format();
      this.recordCount = toCopy.recordCount();
      this.fileSizeInBytes = toCopy.fileSizeInBytes();
      this.columnSizes = toCopy.columnSizes();
      this.valueCounts = toCopy.valueCounts();
      this.nullValueCounts = toCopy.nullValueCounts();
      this.nanValueCounts = toCopy.nanValueCounts();
      this.lowerBounds = toCopy.lowerBounds();
      this.upperBounds = toCopy.upperBounds();
      this.keyMetadata =
          toCopy.keyMetadata() == null ? null : ByteBuffers.copy(toCopy.keyMetadata());
      this.sortOrderId = toCopy.sortOrderId();
      return this;
    }

    public Builder ofPositionDeletes() {
      this.content = FileContent.POSITION_DELETES;
      this.equalityFieldIds = null;
      return this;
    }

    public Builder ofEqualityDeletes(int... fieldIds) {
      this.content = FileContent.EQUALITY_DELETES;
      this.equalityFieldIds = fieldIds;
      return this;
    }

    public Builder withStatus(FileStatus stat) {
      this.filePath = stat.getPath().toString();
      this.fileSizeInBytes = stat.getLen();
      return this;
    }

    public Builder withInputFile(InputFile file) {
      if (file instanceof HadoopInputFile) {
        return withStatus(((HadoopInputFile) file).getStat());
      }

      this.filePath = file.location();
      this.fileSizeInBytes = file.getLength();
      return this;
    }

    public Builder withEncryptedOutputFile(EncryptedOutputFile newEncryptedFile) {
      withInputFile(newEncryptedFile.encryptingOutputFile().toInputFile());
      withEncryptionKeyMetadata(newEncryptedFile.keyMetadata());
      return this;
    }

    public Builder withPath(String newFilePath) {
      this.filePath = newFilePath;
      return this;
    }

    public Builder withFormat(String newFormat) {
      this.format = FileFormat.fromString(newFormat);
      return this;
    }

    public Builder withFormat(FileFormat newFormat) {
      this.format = newFormat;
      return this;
    }

    public Builder withPartition(StructLike newPartition) {
      if (isPartitioned) {
        this.partitionData = DataFiles.copyPartitionData(spec, newPartition, partitionData);
      }
      return this;
    }

    public Builder withRecordCount(long newRecordCount) {
      this.recordCount = newRecordCount;
      return this;
    }

    public Builder withFileSizeInBytes(long newFileSizeInBytes) {
      this.fileSizeInBytes = newFileSizeInBytes;
      return this;
    }

    public Builder withPartitionPath(String newPartitionPath) {
      Preconditions.checkArgument(
          isPartitioned || newPartitionPath.isEmpty(),
          "Cannot add partition data for an unpartitioned table");
      if (!newPartitionPath.isEmpty()) {
        this.partitionData = DataFiles.fillFromPath(spec, newPartitionPath, partitionData);
      }
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      // check for null to avoid NPE when unboxing
      this.recordCount = metrics.recordCount() == null ? -1 : metrics.recordCount();
      this.columnSizes = metrics.columnSizes();
      this.valueCounts = metrics.valueCounts();
      this.nullValueCounts = metrics.nullValueCounts();
      this.nanValueCounts = metrics.nanValueCounts();
      this.lowerBounds = metrics.lowerBounds();
      this.upperBounds = metrics.upperBounds();
      return this;
    }

    public Builder withSplitOffsets(List<Long> offsets) {
      if (offsets != null) {
        this.splitOffsets = ImmutableList.copyOf(offsets);
      } else {
        this.splitOffsets = null;
      }
      return this;
    }

    public Builder withEncryptionKeyMetadata(ByteBuffer newKeyMetadata) {
      this.keyMetadata = newKeyMetadata;
      return this;
    }

    public Builder withEncryptionKeyMetadata(EncryptionKeyMetadata newKeyMetadata) {
      return withEncryptionKeyMetadata(newKeyMetadata.buffer());
    }

    public Builder withSortOrder(SortOrder newSortOrder) {
      if (newSortOrder != null) {
        this.sortOrderId = newSortOrder.orderId();
      }
      return this;
    }

    public Builder withReferencedDataFile(CharSequence newReferencedDataFile) {
      if (newReferencedDataFile != null) {
        this.referencedDataFile = newReferencedDataFile.toString();
      } else {
        this.referencedDataFile = null;
      }
      return this;
    }

    public Builder withContentOffset(long newContentOffset) {
      this.contentOffset = newContentOffset;
      return this;
    }

    public Builder withContentSizeInBytes(long newContentSizeInBytes) {
      this.contentSizeInBytes = newContentSizeInBytes;
      return this;
    }

    public DeleteFile build() {
      Preconditions.checkArgument(filePath != null, "File path is required");
      if (format == null) {
        this.format = FileFormat.fromFileName(filePath);
      }
      Preconditions.checkArgument(content != null, "Delete type is required");
      Preconditions.checkArgument(format != null, "File format is required");
      Preconditions.checkArgument(fileSizeInBytes >= 0, "File size is required");
      Preconditions.checkArgument(recordCount >= 0, "Record count is required");

      if (format == FileFormat.PUFFIN) {
        Preconditions.checkArgument(contentOffset != null, "Content offset is required for DV");
        Preconditions.checkArgument(contentSizeInBytes != null, "Content size is required for DV");
        Preconditions.checkArgument(
            referencedDataFile != null, "Referenced data file is required for DV");
      } else {
        Preconditions.checkArgument(contentOffset == null, "Content offset can only be set for DV");
        Preconditions.checkArgument(
            contentSizeInBytes == null, "Content size can only be set for DV");
      }

      switch (content) {
        case POSITION_DELETES:
          Preconditions.checkArgument(
              sortOrderId == null, "Position delete file should not have sort order");
          break;
        case EQUALITY_DELETES:
          if (sortOrderId == null) {
            sortOrderId = SortOrder.unsorted().orderId();
          }
          break;
        default:
          throw new IllegalStateException("Unknown content type " + content);
      }

      return new GenericDeleteFile(
          specId,
          content,
          filePath,
          format,
          isPartitioned ? DataFiles.copy(spec, partitionData) : null,
          fileSizeInBytes,
          new Metrics(
              recordCount,
              columnSizes,
              valueCounts,
              nullValueCounts,
              nanValueCounts,
              lowerBounds,
              upperBounds),
          equalityFieldIds,
          sortOrderId,
          splitOffsets,
          keyMetadata,
          referencedDataFile,
          contentOffset,
          contentSizeInBytes);
    }
  }
}
