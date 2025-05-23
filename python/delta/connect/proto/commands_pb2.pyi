#
# Copyright (2024) The Delta Lake Project Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
@generated by mypy-protobuf.  Do not edit manually!
isort:skip_file

Copyright (2024) The Delta Lake Project Authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
import builtins
import collections.abc
import delta.connect.proto.proto.base_pb2
import google.protobuf.descriptor
import google.protobuf.internal.containers
import google.protobuf.message
import sys

if sys.version_info >= (3, 8):
    import typing as typing_extensions
else:
    import typing_extensions

DESCRIPTOR: google.protobuf.descriptor.FileDescriptor

class DeltaCommand(google.protobuf.message.Message):
    """Message to hold all command extensions in Delta Connect."""

    DESCRIPTOR: google.protobuf.descriptor.Descriptor

    CLONE_TABLE_FIELD_NUMBER: builtins.int
    VACUUM_TABLE_FIELD_NUMBER: builtins.int
    UPGRADE_TABLE_PROTOCOL_FIELD_NUMBER: builtins.int
    GENERATE_FIELD_NUMBER: builtins.int
    @property
    def clone_table(self) -> global___CloneTable: ...
    @property
    def vacuum_table(self) -> global___VacuumTable: ...
    @property
    def upgrade_table_protocol(self) -> global___UpgradeTableProtocol: ...
    @property
    def generate(self) -> global___Generate: ...
    def __init__(
        self,
        *,
        clone_table: global___CloneTable | None = ...,
        vacuum_table: global___VacuumTable | None = ...,
        upgrade_table_protocol: global___UpgradeTableProtocol | None = ...,
        generate: global___Generate | None = ...,
    ) -> None: ...
    def HasField(
        self,
        field_name: typing_extensions.Literal[
            "clone_table",
            b"clone_table",
            "command_type",
            b"command_type",
            "generate",
            b"generate",
            "upgrade_table_protocol",
            b"upgrade_table_protocol",
            "vacuum_table",
            b"vacuum_table",
        ],
    ) -> builtins.bool: ...
    def ClearField(
        self,
        field_name: typing_extensions.Literal[
            "clone_table",
            b"clone_table",
            "command_type",
            b"command_type",
            "generate",
            b"generate",
            "upgrade_table_protocol",
            b"upgrade_table_protocol",
            "vacuum_table",
            b"vacuum_table",
        ],
    ) -> None: ...
    def WhichOneof(
        self, oneof_group: typing_extensions.Literal["command_type", b"command_type"]
    ) -> (
        typing_extensions.Literal[
            "clone_table", "vacuum_table", "upgrade_table_protocol", "generate"
        ]
        | None
    ): ...

global___DeltaCommand = DeltaCommand

class CloneTable(google.protobuf.message.Message):
    """Command that creates a copy of a DeltaTable in the specified target location."""

    DESCRIPTOR: google.protobuf.descriptor.Descriptor

    class PropertiesEntry(google.protobuf.message.Message):
        DESCRIPTOR: google.protobuf.descriptor.Descriptor

        KEY_FIELD_NUMBER: builtins.int
        VALUE_FIELD_NUMBER: builtins.int
        key: builtins.str
        value: builtins.str
        def __init__(
            self,
            *,
            key: builtins.str = ...,
            value: builtins.str = ...,
        ) -> None: ...
        def ClearField(
            self, field_name: typing_extensions.Literal["key", b"key", "value", b"value"]
        ) -> None: ...

    TABLE_FIELD_NUMBER: builtins.int
    TARGET_FIELD_NUMBER: builtins.int
    VERSION_FIELD_NUMBER: builtins.int
    TIMESTAMP_FIELD_NUMBER: builtins.int
    IS_SHALLOW_FIELD_NUMBER: builtins.int
    REPLACE_FIELD_NUMBER: builtins.int
    PROPERTIES_FIELD_NUMBER: builtins.int
    @property
    def table(self) -> delta.connect.proto.base_pb2.DeltaTable:
        """(Required) The source Delta table to clone."""
    target: builtins.str
    """(Required) Path to the location where the cloned table should be stored."""
    version: builtins.int
    """Clones the source table as of the provided version."""
    timestamp: builtins.str
    """Clones the source table as of the provided timestamp."""
    is_shallow: builtins.bool
    """(Required) Performs a clone when true, this field should always be set to true."""
    replace: builtins.bool
    """(Required) Overwrites the target location when true."""
    @property
    def properties(
        self,
    ) -> google.protobuf.internal.containers.ScalarMap[builtins.str, builtins.str]:
        """(Required) User-defined table properties that override properties with the same key in the
        source table.
        """
    def __init__(
        self,
        *,
        table: delta.connect.proto.base_pb2.DeltaTable | None = ...,
        target: builtins.str = ...,
        version: builtins.int = ...,
        timestamp: builtins.str = ...,
        is_shallow: builtins.bool = ...,
        replace: builtins.bool = ...,
        properties: collections.abc.Mapping[builtins.str, builtins.str] | None = ...,
    ) -> None: ...
    def HasField(
        self,
        field_name: typing_extensions.Literal[
            "table",
            b"table",
            "timestamp",
            b"timestamp",
            "version",
            b"version",
            "version_or_timestamp",
            b"version_or_timestamp",
        ],
    ) -> builtins.bool: ...
    def ClearField(
        self,
        field_name: typing_extensions.Literal[
            "is_shallow",
            b"is_shallow",
            "properties",
            b"properties",
            "replace",
            b"replace",
            "table",
            b"table",
            "target",
            b"target",
            "timestamp",
            b"timestamp",
            "version",
            b"version",
            "version_or_timestamp",
            b"version_or_timestamp",
        ],
    ) -> None: ...
    def WhichOneof(
        self,
        oneof_group: typing_extensions.Literal["version_or_timestamp", b"version_or_timestamp"],
    ) -> typing_extensions.Literal["version", "timestamp"] | None: ...

global___CloneTable = CloneTable

class VacuumTable(google.protobuf.message.Message):
    """Command that deletes files and directories in the table that are not needed by the table for
    maintaining older versions up to the given retention threshold.
    """

    DESCRIPTOR: google.protobuf.descriptor.Descriptor

    TABLE_FIELD_NUMBER: builtins.int
    RETENTION_HOURS_FIELD_NUMBER: builtins.int
    @property
    def table(self) -> delta.connect.proto.base_pb2.DeltaTable:
        """(Required) The Delta table to vacuum."""
    retention_hours: builtins.float
    """(Optional) Number of hours retain history for. If not specified, then the default retention
    period will be used.
    """
    def __init__(
        self,
        *,
        table: delta.connect.proto.base_pb2.DeltaTable | None = ...,
        retention_hours: builtins.float | None = ...,
    ) -> None: ...
    def HasField(
        self,
        field_name: typing_extensions.Literal[
            "_retention_hours",
            b"_retention_hours",
            "retention_hours",
            b"retention_hours",
            "table",
            b"table",
        ],
    ) -> builtins.bool: ...
    def ClearField(
        self,
        field_name: typing_extensions.Literal[
            "_retention_hours",
            b"_retention_hours",
            "retention_hours",
            b"retention_hours",
            "table",
            b"table",
        ],
    ) -> None: ...
    def WhichOneof(
        self, oneof_group: typing_extensions.Literal["_retention_hours", b"_retention_hours"]
    ) -> typing_extensions.Literal["retention_hours"] | None: ...

global___VacuumTable = VacuumTable

class UpgradeTableProtocol(google.protobuf.message.Message):
    """Command to updates the protocol version of the table so that new features can be used."""

    DESCRIPTOR: google.protobuf.descriptor.Descriptor

    TABLE_FIELD_NUMBER: builtins.int
    READER_VERSION_FIELD_NUMBER: builtins.int
    WRITER_VERSION_FIELD_NUMBER: builtins.int
    @property
    def table(self) -> delta.connect.proto.base_pb2.DeltaTable:
        """(Required) The Delta table to upgrade the protocol of."""
    reader_version: builtins.int
    """(Required) The minimum required reader protocol version."""
    writer_version: builtins.int
    """(Required) The minimum required writer protocol version."""
    def __init__(
        self,
        *,
        table: delta.connect.proto.base_pb2.DeltaTable | None = ...,
        reader_version: builtins.int = ...,
        writer_version: builtins.int = ...,
    ) -> None: ...
    def HasField(
        self, field_name: typing_extensions.Literal["table", b"table"]
    ) -> builtins.bool: ...
    def ClearField(
        self,
        field_name: typing_extensions.Literal[
            "reader_version",
            b"reader_version",
            "table",
            b"table",
            "writer_version",
            b"writer_version",
        ],
    ) -> None: ...

global___UpgradeTableProtocol = UpgradeTableProtocol

class Generate(google.protobuf.message.Message):
    """Command that generates manifest files for a given Delta table."""

    DESCRIPTOR: google.protobuf.descriptor.Descriptor

    TABLE_FIELD_NUMBER: builtins.int
    MODE_FIELD_NUMBER: builtins.int
    @property
    def table(self) -> delta.connect.proto.base_pb2.DeltaTable:
        """(Required) The Delta table to generate the manifest files for."""
    mode: builtins.str
    """(Required) The type of manifest file to be generated."""
    def __init__(
        self,
        *,
        table: delta.connect.proto.base_pb2.DeltaTable | None = ...,
        mode: builtins.str = ...,
    ) -> None: ...
    def HasField(
        self, field_name: typing_extensions.Literal["table", b"table"]
    ) -> builtins.bool: ...
    def ClearField(
        self, field_name: typing_extensions.Literal["mode", b"mode", "table", b"table"]
    ) -> None: ...

global___Generate = Generate
