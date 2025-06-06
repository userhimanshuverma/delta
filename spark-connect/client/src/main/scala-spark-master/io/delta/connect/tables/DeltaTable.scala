/*
 * Copyright (2024) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.tables

import scala.collection.JavaConverters._

import io.delta.connect.proto
import io.delta.connect.spark.{proto => spark_proto}

import org.apache.spark.annotation.Evolving
import org.apache.spark.sql.{functions, Column, DataFrame, Dataset, Row, SparkSession}
import org.apache.spark.sql.catalyst.encoders.AgnosticEncoders.PrimitiveBooleanEncoder
import org.apache.spark.sql.connect.ColumnNodeToProtoConverter.toExpr
import org.apache.spark.sql.connect.ConnectConversions._
import org.apache.spark.sql.connect.delta.ImplicitProtoConversions._

/**
 * Main class for programmatically interacting with Delta tables.
 * You can create DeltaTable instances using the static methods.
 * {{{
 *   DeltaTable.forPath(sparkSession, pathToTheDeltaTable)
 * }}}
 *
 * @since 4.0.0
 */
class DeltaTable private[tables](
    private val df: Dataset[Row],
    private val table: proto.DeltaTable)
  extends Serializable {

  private def sparkSession: SparkSession = df.sparkSession

  /**
   * Apply an alias to the DeltaTable. This is similar to `Dataset.as(alias)` or
   * SQL `tableName AS alias`.
   *
   * @since 4.0.0
   */
  def as(alias: String): DeltaTable = new DeltaTable(df.as(alias), table)

  /**
   * Apply an alias to the DeltaTable. This is similar to `Dataset.as(alias)` or
   * SQL `tableName AS alias`.
   *
   * @since 4.0.0
   */
  def alias(alias: String): DeltaTable = as(alias)

  /**
   * Get a DataFrame (that is, Dataset[Row]) representation of this Delta table.
   *
   * @since 4.0.0
   */
  def toDF: Dataset[Row] = df

  /**
   * Helper method for the vacuum APIs.
   *
   * @param retentionHours The retention threshold in hours. Files required by the table for
   *                       reading versions earlier than this will be preserved and the
   *                       rest of them will be deleted.
   *
   * @since 4.0.0
   */
  private def executeVacuum(retentionHours: Option[Double]): DataFrame = {
    val vacuum = proto.VacuumTable
      .newBuilder()
      .setTable(table)
    retentionHours.foreach(vacuum.setRetentionHours)
    val command = proto.DeltaCommand
      .newBuilder()
      .setVacuumTable(vacuum)
      .build()
    execute(command)
    sparkSession.emptyDataFrame
  }

  /**
   * Recursively delete files and directories in the table that are not needed by the table for
   * maintaining older versions up to the given retention threshold. This method will return an
   * empty DataFrame on successful completion.
   *
   * @param retentionHours The retention threshold in hours. Files required by the table for
   *                       reading versions earlier than this will be preserved and the
   *                       rest of them will be deleted.
   * @since 4.0.0
   */
  def vacuum(retentionHours: Double): DataFrame = {
    executeVacuum(Some(retentionHours))
  }

  /**
   * Recursively delete files and directories in the table that are not needed by the table for
   * maintaining older versions up to the given retention threshold. This method will return an
   * empty DataFrame on successful completion.
   *
   * note: This will use the default retention period of 7 days.
   *
   * @since 4.0.0
   */
  def vacuum(): DataFrame = {
    executeVacuum(None)
  }

  /**
   * Helper method for the history APIs.
   *
   * @param limit The number of previous commands to get history for.
   *
   * @since 4.0.0
   */
  private def executeHistory(limit: Option[Int]): DataFrame = {
    val describeHistory = proto.DescribeHistory
      .newBuilder()
      .setTable(table)
    val relation = proto.DeltaRelation.newBuilder().setDescribeHistory(describeHistory).build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    val df = sparkSession.newDataFrame(_.mergeFrom(sparkRelation))
    limit match {
      case Some(limit) => df.limit(limit)
      case None => df
    }
  }

  /**
   * Get the information of the latest `limit` commits on this table as a Spark DataFrame.
   * The information is in reverse chronological order.
   *
   * @param limit The number of previous commands to get history for.
   *
   * @since 4.0.0
   */
  def history(limit: Int): DataFrame = {
    executeHistory(Some(limit))
  }

  /**
   * Get the information available commits on this table as a Spark DataFrame.
   * The information is in reverse chronological order.
   *
   * @since 4.0.0
   */
  def history(): DataFrame = {
    executeHistory(limit = None)
  }

  /**
   * :: Evolving ::
   *
   * Get the details of a Delta table such as the format, name, and size.
   *
   * @since 4.0.0
   */
  @Evolving
  def detail(): DataFrame = {
    val describeDetail = proto.DescribeDetail
      .newBuilder()
      .setTable(table)
    val relation = proto.DeltaRelation.newBuilder().setDescribeDetail(describeDetail).build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    sparkSession.newDataFrame(_.mergeFrom(sparkRelation))
  }

  /**
   * Helper method for the delete APIs.
   *
   * @param condition Boolean SQL expression.
   *
   * @since 4.0.0
   */
  private def executeDelete(condition: Option[Column]): Unit = {
    val delete = proto.DeleteFromTable
      .newBuilder()
      .setTarget(df.plan.getRoot)
    condition.foreach(c => delete.setCondition(toExpr(c)))
    val relation = proto.DeltaRelation.newBuilder().setDeleteFromTable(delete).build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    sparkSession.newDataFrame(_.mergeFrom(sparkRelation)).collect()
  }

  /**
   * Delete data from the table that match the given `condition`.
   *
   * @param condition Boolean SQL expression.
   *
   * @since 4.0.0
   */
  def delete(condition: String): Unit = {
    delete(functions.expr(condition))
  }

  /**
   * Delete data from the table that match the given `condition`.
   *
   * @param condition Boolean SQL expression.
   *
   * @since 4.0.0
   */
  def delete(condition: Column): Unit = {
    executeDelete(condition = Some(condition))
  }

  /**
   * Delete data from the table.
   *
   * @since 4.0.0
   */
  def delete(): Unit = {
    executeDelete(condition = None)
  }

  /**
   * Helper method for the update APIs.
   *
   * @param condition boolean expression as Column object specifying which rows to update.
   * @param set       rules to update a row as a Scala map between target column names and
   *                  corresponding update expressions as Column objects.
   *
   * @since 4.0.0
   */
  private def executeUpdate(condition: Option[Column], set: Map[String, Column]): Unit = {
    val assignments = set.toSeq.map { case (field, value) =>
      proto.Assignment
        .newBuilder()
        .setField(toExpr(functions.expr(field)))
        .setValue(toExpr(value))
        .build()
    }
    val update = proto.UpdateTable
      .newBuilder()
      .setTarget(df.plan.getRoot)
      .addAllAssignments(assignments.asJava)
    condition.foreach(c => update.setCondition(toExpr(c)))
    val relation = proto.DeltaRelation.newBuilder().setUpdateTable(update).build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    sparkSession.newDataFrame(_.mergeFrom(sparkRelation)).collect()
  }

  /**
   * Update rows in the table based on the rules defined by `set`.
   *
   * Scala example to increment the column `data`.
   * {{{
   *    import org.apache.spark.sql.functions._
   *
   *    deltaTable.update(Map("data" -> col("data") + 1))
   * }}}
   *
   * @param set rules to update a row as a Scala map between target column names and
   *            corresponding update expressions as Column objects.
   *
   * @since 4.0.0
   */
  def update(set: Map[String, Column]): Unit = {
    executeUpdate(condition = None, set)
  }

  /**
   * Update rows in the table based on the rules defined by `set`.
   *
   * Java example to increment the column `data`.
   * {{{
   *    import org.apache.spark.sql.Column;
   *    import org.apache.spark.sql.functions;
   *
   *    deltaTable.update(
   *      new HashMap<String, Column>() {{
   *        put("data", functions.col("data").plus(1));
   *      }}
   *    );
   * }}}
   *
   * @param set rules to update a row as a Java map between target column names and
   *            corresponding update expressions as Column objects.
   *
   * @since 4.0.0
   */
  def update(set: java.util.Map[String, Column]): Unit = {
    update(set.asScala.asInstanceOf[Map[String, Column]])
  }

  /**
   * Update data from the table on the rows that match the given `condition`
   * based on the rules defined by `set`.
   *
   * Scala example to increment the column `data`.
   * {{{
   *    import org.apache.spark.sql.functions._
   *
   *    deltaTable.update(
   *      col("date") > "2018-01-01",
   *      Map("data" -> col("data") + 1))
   * }}}
   *
   * @param condition boolean expression as Column object specifying which rows to update.
   * @param set       rules to update a row as a Scala map between target column names and
   *                  corresponding update expressions as Column objects.
   *
   * @since 4.0.0
   */
  def update(condition: Column, set: Map[String, Column]): Unit = {
    executeUpdate(Some(condition), set)
  }

  /**
   * Update data from the table on the rows that match the given `condition`
   * based on the rules defined by `set`.
   *
   * Java example to increment the column `data`.
   * {{{
   *    import org.apache.spark.sql.Column;
   *    import org.apache.spark.sql.functions;
   *
   *    deltaTable.update(
   *      functions.col("date").gt("2018-01-01"),
   *      new HashMap<String, Column>() {{
   *        put("data", functions.col("data").plus(1));
   *      }}
   *    );
   * }}}
   *
   * @param condition boolean expression as Column object specifying which rows to update.
   * @param set       rules to update a row as a Java map between target column names and
   *                  corresponding update expressions as Column objects.
   *
   * @since 4.0.0
   */
  def update(condition: Column, set: java.util.Map[String, Column]): Unit = {
    executeUpdate(Some(condition), set.asScala.toMap)
  }

  /**
   * Update rows in the table based on the rules defined by `set`.
   *
   * Scala example to increment the column `data`.
   * {{{
   *    deltaTable.updateExpr(Map("data" -> "data + 1")))
   * }}}
   *
   * @param set rules to update a row as a Scala map between target column names and
   *            corresponding update expressions as SQL formatted strings.
   *
   * @since 4.0.0
   */
  def updateExpr(set: Map[String, String]): Unit = {
    update(toStrColumnMap(set))
  }

  /**
   * Update rows in the table based on the rules defined by `set`.
   *
   * Java example to increment the column `data`.
   * {{{
   *    deltaTable.updateExpr(
   *      new HashMap<String, String>() {{
   *        put("data", "data + 1");
   *      }}
   *    );
   * }}}
   *
   * @param set rules to update a row as a Java map between target column names and
   *            corresponding update expressions as SQL formatted strings.
   *
   * @since 4.0.0
   */
  def updateExpr(set: java.util.Map[String, String]): Unit = {
    update(toStrColumnMap(set.asScala.toMap))
  }

  /**
   * Update data from the table on the rows that match the given `condition`,
   * which performs the rules defined by `set`.
   *
   * Scala example to increment the column `data`.
   * {{{
   *    deltaTable.update(
   *      "date > '2018-01-01'",
   *      Map("data" -> "data + 1"))
   * }}}
   *
   * @param condition boolean expression as SQL formatted string object specifying
   *                  which rows to update.
   * @param set       rules to update a row as a Scala map between target column names and
   *                  corresponding update expressions as SQL formatted strings.
   *
   * @since 4.0.0
   */
  def updateExpr(condition: String, set: Map[String, String]): Unit = {
    executeUpdate(Some(functions.expr(condition)), toStrColumnMap(set))
  }

  /**
   * Update data from the table on the rows that match the given `condition`,
   * which performs the rules defined by `set`.
   *
   * Java example to increment the column `data`.
   * {{{
   *    deltaTable.update(
   *      "date > '2018-01-01'",
   *      new HashMap<String, String>() {{
   *        put("data", "data + 1");
   *      }}
   *    );
   * }}}
   *
   * @param condition boolean expression as SQL formatted string object specifying
   *                  which rows to update.
   * @param set       rules to update a row as a Java map between target column names and
   *                  corresponding update expressions as SQL formatted strings.
   *
   * @since 4.0.0
   */
  def updateExpr(condition: String, set: java.util.Map[String, String]): Unit = {
    executeUpdate(Some(functions.expr(condition)), toStrColumnMap(set.asScala.toMap))
  }

  private def executeClone(
      target: String,
      isShallow: Boolean,
      replace: Boolean,
      properties: Map[String, String],
      versionAsOf: Option[Int] = None,
      timestampAsOf: Option[String] = None): DeltaTable = {
    val clone = proto.CloneTable
      .newBuilder()
      .setTable(table)
      .setTarget(target)
      .setIsShallow(isShallow)
      .setReplace(replace)
      .putAllProperties(properties.asJava)
    versionAsOf.foreach(clone.setVersion)
    timestampAsOf.foreach(clone.setTimestamp)
    val command = proto.DeltaCommand.newBuilder().setCloneTable(clone).build()
    execute(command)
    DeltaTable.forPath(sparkSession, target)
  }

  /**
   * Clone a DeltaTable to a given destination to mirror the existing table's data and metadata.
   *
   * Specifying properties here means that the target will override any properties with the same key
   * in the source table with the user-defined properties.
   *
   * An example would be
   * {{{
   *  io.delta.tables.DeltaTable.clone(
   *   "/some/path/to/table",
   *   true,
   *   true,
   *   Map("foo" -> "bar"))
   * }}}
   *
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   * @param properties The table properties to override in the clone.
   *
   * @since 4.0.0
   */
  def clone(
      target: String,
      isShallow: Boolean,
      replace: Boolean,
      properties: Map[String, String]): DeltaTable = {
    executeClone(target, isShallow, replace, properties, versionAsOf = None, timestampAsOf = None)
  }

  /**
   * Clone a DeltaTable to a given destination to mirror the existing table's data and metadata.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.clone(
   *     "/some/path/to/table",
   *     true,
   *     true)
   * }}}
   *
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   *
   * @since 4.0.0
   */
  def clone(target: String, isShallow: Boolean, replace: Boolean): DeltaTable = {
    clone(target, isShallow, replace, properties = Map.empty[String, String])
  }

  /**
   * Clone a DeltaTable to a given destination to mirror the existing table's data and metadata.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.clone(
   *     "/some/path/to/table",
   *     true)
   * }}}
   *
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   *
   * @since 4.0.0
   */
  def clone(target: String, isShallow: Boolean): DeltaTable = {
    clone(target, isShallow, replace = false)
  }

  /**
   * Clone a DeltaTable at a specific version to a given destination to mirror the existing
   * table's data and metadata at that version.
   *
   * Specifying properties here means that the target will override any properties with the same key
   * in the source table with the user-defined properties.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtVersion(
   *     5,
   *     "/some/path/to/table",
   *     true,
   *     true,
   *     Map("foo" -> "bar"))
   * }}}
   *
   * @param version The version of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   * @param properties The table properties to override in the clone.
   *
   * @since 4.0.0
   */
  def cloneAtVersion(
      version: Int,
      target: String,
      isShallow: Boolean,
      replace: Boolean,
      properties: Map[String, String]): DeltaTable = {
    executeClone(target, isShallow, replace, properties, versionAsOf = Some(version), timestampAsOf = None)
  }

  /**
   * Clone a DeltaTable at a specific version to a given destination to mirror the existing
   * table's data and metadata at that version.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtVersion(
   *     5,
   *     "/some/path/to/table",
   *     true,
   *     true)
   * }}}
   *
   * @param version The version of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   *
   * @since 4.0.0
   */
  def cloneAtVersion(
      version: Int, target: String, isShallow: Boolean, replace: Boolean): DeltaTable = {
    cloneAtVersion(version, target, isShallow, replace, properties = Map.empty[String, String])
  }

  /**
   * Clone a DeltaTable at a specific version to a given destination to mirror the existing
   * table's data and metadata at that version.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtVersion(
   *     5,
   *     "/some/path/to/table",
   *     true)
   * }}}
   *
   * @param version The version of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   *
   * @since 4.0.0
   */
  def cloneAtVersion(version: Int, target: String, isShallow: Boolean): DeltaTable = {
    cloneAtVersion(version, target, isShallow, replace = false)
  }

  /**
   * Clone a DeltaTable at a specific timestamp to a given destination to mirror the existing
   * table's data and metadata at that timestamp.
   *
   * Timestamp can be of the format yyyy-MM-dd or yyyy-MM-dd HH:mm:ss.
   *
   * Specifying properties here means that the target will override any properties with the same key
   * in the source table with the user-defined properties.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtTimestamp(
   *     "2019-01-01",
   *     "/some/path/to/table",
   *     true,
   *     true,
   *     Map("foo" -> "bar"))
   * }}}
   *
   * @param timestamp The timestamp of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   * @param properties The table properties to override in the clone.
   *
   * @since 4.0.0
   */
  def cloneAtTimestamp(
      timestamp: String,
      target: String,
      isShallow: Boolean,
      replace: Boolean,
      properties: Map[String, String]): DeltaTable = {
    executeClone(
      target, isShallow, replace, properties, versionAsOf = None, timestampAsOf = Some(timestamp))
  }

  /**
   * Clone a DeltaTable at a specific timestamp to a given destination to mirror the existing
   * table's data and metadata at that timestamp.
   *
   * Timestamp can be of the format yyyy-MM-dd or yyyy-MM-dd HH:mm:ss.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtTimestamp(
   *     "2019-01-01",
   *     "/some/path/to/table",
   *     true,
   *     true)
   * }}}
   *
   * @param timestamp The timestamp of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   * @param replace Whether to replace the destination with the clone command.
   *
   * @since 4.0.0
   */
  def cloneAtTimestamp(
      timestamp: String, target: String, isShallow: Boolean, replace: Boolean): DeltaTable = {
    cloneAtTimestamp(timestamp, target, isShallow, replace, properties = Map.empty[String, String])
  }

  /**
   * Clone a DeltaTable at a specific timestamp to a given destination to mirror the existing
   * table's data and metadata at that timestamp.
   *
   * Timestamp can be of the format yyyy-MM-dd or yyyy-MM-dd HH:mm:ss.
   *
   * An example would be
   * {{{
   *   io.delta.tables.DeltaTable.cloneAtTimestamp(
   *     "2019-01-01",
   *     "/some/path/to/table",
   *     true)
   * }}}
   *
   * @param timestamp The timestamp of this table to clone from.
   * @param target The path or table name to create the clone.
   * @param isShallow Whether to create a shallow clone or a deep clone.
   *
   * @since 4.0.0
   */
  def cloneAtTimestamp(timestamp: String, target: String, isShallow: Boolean): DeltaTable = {
    cloneAtTimestamp(timestamp, target, isShallow, replace = false)
  }

  /**
   * Helper method for the restoreToVersion and restoreToTimestamp APIs.
   *
   * @param version The version number of the older version of the table to restore to.
   * @param timestamp The timestamp of the older version of the table to restore to.
   *
   * @since 4.0.0
   */
  private def executeRestore(version: Option[Long], timestamp: Option[String]): DataFrame = {
    val restore = proto.RestoreTable
      .newBuilder()
      .setTable(table)
    version.foreach(restore.setVersion)
    timestamp.foreach(restore.setTimestamp)
    val relation = proto.DeltaRelation.newBuilder().setRestoreTable(restore).build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    val result = sparkSession.newDataFrame(_.mergeFrom(sparkRelation)).collectResult()
    val data = try {
      result.toArray.toSeq.asJava
    } finally {
      result.close()
    }
    sparkSession.createDataFrame(data, result.schema)
  }

  /**
   * Restore the DeltaTable to an older version of the table specified by version number.
   *
   * An example would be
   * {{{ io.delta.tables.DeltaTable.restoreToVersion(7) }}}
   *
   * @since 4.0.0
   */
  def restoreToVersion(version: Long): DataFrame = {
    executeRestore(version = Some(version), timestamp = None)
  }

  /**
   * Restore the DeltaTable to an older version of the table specified by a timestamp.
   *
   * Timestamp can be of the format yyyy-MM-dd or yyyy-MM-dd HH:mm:ss
   *
   * An example would be
   * {{{ io.delta.tables.DeltaTable.restoreToTimestamp("2019-01-01") }}}
   *
   * @since 4.0.0
   */
  def restoreToTimestamp(timestamp: String): DataFrame = {
    executeRestore(version = None, timestamp = Some(timestamp))
  }

  /**
   * Converts a map of strings to expressions as SQL formatted string
   * into a map of strings to Column objects.
   *
   * @param map A map where the value is an expression as SQL formatted string.
   * @return A map where the value is a Column object created from the expression.
   */
  private def toStrColumnMap(map: Map[String, String]): Map[String, Column] = {
    map.toSeq.map { case (k, v) => k -> functions.expr(v) }.toMap
  }

  /**
   * Generate a manifest for the given Delta Table
   *
   * @param mode Specifies the mode for the generation of the manifest.
   *             The valid modes are as follows (not case sensitive):
   *              - "symlink_format_manifest" : This will generate manifests in symlink format
   *                for Presto and Athena read support.
   *                See the online documentation for more information.
   * @since 4.0.0
   */
  def generate(mode: String): Unit = {
    val generate = proto.Generate
      .newBuilder()
      .setTable(table)
      .setMode(mode)
    val command = proto.DeltaCommand.newBuilder().setGenerate(generate).build()
    execute(command)
  }

  /**
   * Updates the protocol version of the table to leverage new features. Upgrading the reader
   * version will prevent all clients that have an older version of Delta Lake from accessing this
   * table. Upgrading the writer version will prevent older versions of Delta Lake to write to this
   * table. The reader or writer version cannot be downgraded.
   *
   * See online documentation and Delta's protocol specification at PROTOCOL.md for more details.
   *
   * @since 4.0.0
   */
  def upgradeTableProtocol(readerVersion: Int, writerVersion: Int): Unit = {
    val upgrade = proto.UpgradeTableProtocol
      .newBuilder()
      .setTable(table)
      .setReaderVersion(readerVersion)
      .setWriterVersion(writerVersion)
    val command = proto.DeltaCommand.newBuilder().setUpgradeTableProtocol(upgrade).build()
    execute(command)
  }

  private def execute(command: proto.DeltaCommand): Unit = {
    val extension = com.google.protobuf.Any.pack(command)
    val sparkCommand = spark_proto.Command
      .newBuilder()
      .setExtension(extension)
      .build()
    sparkSession.execute(sparkCommand)
  }
}

/**
 * Companion object to create DeltaTable instances.
 *
 * {{{
 *   DeltaTable.forPath(sparkSession, pathToTheDeltaTable)
 * }}}
 *
 * @since 4.0.0
 */
object DeltaTable {
  /**
   * Instantiate a [[DeltaTable]] object representing the data at the given path, If the given
   * path is invalid (i.e. either no table exists or an existing table is not a Delta table),
   * it throws a `not a Delta table` error.
   *
   * Note: This uses the active SparkSession in the current thread to read the table data. Hence,
   * this throws error if active SparkSession has not been set, that is,
   * `SparkSession.getActiveSession()` is empty.
   *
   * @since 4.0.0
   */
  def forPath(path: String): DeltaTable = {
    val sparkSession = SparkSession.getActiveSession.getOrElse {
      throw new IllegalArgumentException("Could not find active SparkSession")
    }
    forPath(sparkSession, path)
  }

  /**
   * Instantiate a [[DeltaTable]] object representing the data at the given path, If the given
   * path is invalid (i.e. either no table exists or an existing table is not a Delta table),
   * it throws a `not a Delta table` error.
   *
   * @since 4.0.0
   */
  def forPath(sparkSession: SparkSession, path: String): DeltaTable = {
    forPath(sparkSession, path, Map.empty[String, String])
  }

  /**
   * Instantiate a [[DeltaTable]] object representing the data at the given path, If the given
   * path is invalid (i.e. either no table exists or an existing table is not a Delta table),
   * it throws a `not a Delta table` error.
   *
   * @param hadoopConf Hadoop configuration starting with "fs." or "dfs." will be picked up
   *                   by `DeltaTable` to access the file system when executing queries.
   *                   Other configurations will not be allowed.
   *
   * {{{
   *   val hadoopConf = Map(
   *     "fs.s3a.access.key" -> "<access-key>",
   *     "fs.s3a.secret.key" -> "<secret-key>"
   *   )
   *   DeltaTable.forPath(spark, "/path/to/table", hadoopConf)
   * }}}
   *
   * @since 4.0.0
   */
  def forPath(
      sparkSession: SparkSession,
      path: String,
      hadoopConf: scala.collection.Map[String, String]): DeltaTable = {
    val table = proto.DeltaTable
      .newBuilder()
      .setPath(
        proto.DeltaTable.Path
          .newBuilder().setPath(path)
          .putAllHadoopConf(hadoopConf.asJava))
      .build()
    forTable(sparkSession, table)
  }

  /**
   * Java friendly API to instantiate a [[DeltaTable]] object representing the data at the given
   * path, If the given path is invalid (i.e. either no table exists or an existing table is not a
   * Delta table), it throws a `not a Delta table` error.
   *
   * @param hadoopConf Hadoop configuration starting with "fs." or "dfs." will be picked up
   *                   by `DeltaTable` to access the file system when executing queries.
   *                   Other configurations will be ignored.
   *
   * {{{
   *   val hadoopConf = Map(
   *     "fs.s3a.access.key" -> "<access-key>",
   *     "fs.s3a.secret.key", "<secret-key>"
   *   )
   *   DeltaTable.forPath(spark, "/path/to/table", hadoopConf)
   * }}}
   *
   * @since 4.0.0
   */
  def forPath(
      sparkSession: SparkSession,
      path: String,
      hadoopConf: java.util.Map[String, String]): DeltaTable = {
    val fsOptions = hadoopConf.asScala.toMap
    forPath(sparkSession, path, fsOptions)
  }

  /**
   * Instantiate a [[DeltaTable]] object using the given table name. If the given
   * tableOrViewName is invalid (i.e. either no table exists or an existing table is not a
   * Delta table), it throws a `not a Delta table` error. Note: Passing a view name will also
   * result in this error as views are not supported.
   *
   * The given tableOrViewName can also be the absolute path of a delta datasource (i.e.
   * delta.`path`), If so, instantiate a [[DeltaTable]] object representing the data at
   * the given path (consistent with the [[forPath]]).
   *
   * Note: This uses the active SparkSession in the current thread to read the table data. Hence,
   * this throws error if active SparkSession has not been set, that is,
   * `SparkSession.getActiveSession()` is empty.
   *
   * @since 4.0.0
   */
  def forName(tableOrViewName: String): DeltaTable = {
    val sparkSession = SparkSession.getActiveSession.getOrElse {
      throw new IllegalArgumentException("Could not find active SparkSession")
    }
    forName(sparkSession, tableOrViewName)
  }

  /**
   * Instantiate a [[DeltaTable]] object using the given table name using the given
   * SparkSession. If the given tableName is invalid (i.e. either no table exists or an
   * existing table is not a Delta table), it throws a `not a Delta table` error. Note:
   * Passing a view name will also result in this error as views are not supported.
   *
   * The given tableName can also be the absolute path of a delta datasource (i.e.
   * delta.`path`), If so, instantiate a [[DeltaTable]] object representing the data at
   * the given path (consistent with the [[forPath]]).
   *
   * @since 4.0.0
   */
  def forName(sparkSession: SparkSession, tableName: String): DeltaTable = {
    val table = proto.DeltaTable
      .newBuilder()
      .setTableOrViewName(tableName)
      .build()
    forTable(sparkSession, table)
  }

  private def forTable(sparkSession: SparkSession, table: proto.DeltaTable): DeltaTable = {
    val relation = proto.DeltaRelation
      .newBuilder()
      .setScan(proto.Scan.newBuilder().setTable(table))
      .build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    val df = sparkSession.newDataFrame(_.mergeFrom(sparkRelation))
    new DeltaTable(df, table)
  }

  /**
   * Check if the provided `identifier` string, in this case a file path,
   * is the root of a Delta table using the given SparkSession.
   *
   * An example would be
   * {{{
   *   DeltaTable.isDeltaTable(spark, "path/to/table")
   * }}}
   *
   * @since 4.0.0
   */
  def isDeltaTable(sparkSession: SparkSession, identifier: String): Boolean = {
    val relation = proto.DeltaRelation
      .newBuilder()
      .setIsDeltaTable(proto.IsDeltaTable.newBuilder().setPath(identifier))
      .build()
    val extension = com.google.protobuf.Any.pack(relation)
    val sparkRelation = spark_proto.Relation.newBuilder().setExtension(extension).build()
    sparkSession.newDataset(PrimitiveBooleanEncoder)(_.mergeFrom(sparkRelation)).head()
  }

  /**
   * Check if the provided `identifier` string, in this case a file path,
   * is the root of a Delta table.
   *
   * Note: This uses the active SparkSession in the current thread to search for the table. Hence,
   * this throws error if active SparkSession has not been set, that is,
   * `SparkSession.getActiveSession()` is empty.
   *
   * An example would be
   * {{{
   *   DeltaTable.isDeltaTable(spark, "/path/to/table")
   * }}}
   *
   * @since 4.0.0
   */
  def isDeltaTable(identifier: String): Boolean = {
    val sparkSession = SparkSession.getActiveSession.getOrElse {
      throw new IllegalArgumentException("Could not find active SparkSession")
    }
    isDeltaTable(sparkSession, identifier)
  }
}
